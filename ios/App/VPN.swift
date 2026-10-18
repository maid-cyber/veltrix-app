import Foundation
import NetworkExtension
import SwiftUI

// MARK: - sing-box konfiguracija (isti princip kao Android aplikacija)

enum ConfigBuilder {
    static func outbound(_ server: Server, _ proto: String) -> [String: Any] {
        let list = server.outbounds
        let pick = list.first(where: { ($0["type"] as? String) == proto })
            ?? list.first(where: { ($0["type"] as? String) == "vless" })
            ?? list.first ?? [:]
        var o = pick
        o["tag"] = "proxy"
        return o
    }

    static func build(_ server: Server, proto: String = "vless") -> String {
        let config: [String: Any] = [
            "log": ["level": "warn"],
            "dns": [
                "servers": [
                    ["type": "udp", "tag": "remote", "server": "1.1.1.1", "detour": "proxy"],
                    ["type": "local", "tag": "local"],
                ],
                "final": "remote",
                "strategy": "ipv4_only",
            ],
            "inbounds": [[
                "type": "tun", "tag": "tun-in", "address": ["172.19.0.1/30"], "mtu": 9000,
                "auto_route": true, "strict_route": true, "stack": "mixed",
            ]],
            "outbounds": [outbound(server, proto), ["type": "direct", "tag": "direct"]],
            "route": [
                "rules": [
                    ["action": "sniff"],
                    ["protocol": "dns", "action": "hijack-dns"],
                    ["ip_is_private": true, "outbound": "direct"],
                ],
                "final": "proxy",
                "auto_detect_interface": true,
                "default_domain_resolver": "local",
            ],
        ]
        let data = (try? JSONSerialization.data(withJSONObject: config, options: [.sortedKeys])) ?? Data()
        return String(data: data, encoding: .utf8) ?? "{}"
    }
}

// MARK: - iOS VPN profil (NETunnelProviderManager)

@MainActor
final class VPNManager: ObservableObject {
    @Published var status: NEVPNStatus = .invalid
    @Published var connectedAt: Date?
    private var manager: NETunnelProviderManager?
    private var observer: NSObjectProtocol?

    init() {
        observer = NotificationCenter.default.addObserver(forName: .NEVPNStatusDidChange, object: nil, queue: .main) { [weak self] n in
            guard let conn = n.object as? NEVPNConnection else { return }
            Task { @MainActor in
                self?.status = conn.status
                self?.connectedAt = conn.connectedDate
            }
        }
        Task { await load() }
    }

    func load() async {
        let list = (try? await NETunnelProviderManager.loadAllFromPreferences()) ?? []
        manager = list.first
        if let m = manager {
            status = m.connection.status
            connectedAt = m.connection.connectedDate
        }
    }

    private func ensure(brand: String) async throws -> NETunnelProviderManager {
        if let m = manager { return m }
        let m = NETunnelProviderManager()
        let p = NETunnelProviderProtocol()
        p.providerBundleIdentifier = AppConst.tunnelBundle
        p.serverAddress = brand
        m.protocolConfiguration = p
        m.localizedDescription = brand
        m.isEnabled = true
        try await m.saveToPreferences()
        try await m.loadFromPreferences()
        manager = m
        return m
    }

    func connect(config: String, brand: String) async throws {
        let m = try await ensure(brand: brand)
        if !m.isEnabled {
            m.isEnabled = true
            try await m.saveToPreferences()
            try await m.loadFromPreferences()
        }
        try m.connection.startVPNTunnel(options: ["configContent": config as NSString])
    }

    /** Nova lokacija dok je VPN spojen: tunel samo učita novu konfiguraciju. */
    func reload(config: String) {
        guard let s = manager?.connection as? NETunnelProviderSession, let d = config.data(using: .utf8) else { return }
        try? s.sendProviderMessage(d) { _ in }
    }

    func disconnect() {
        manager?.connection.stopVPNTunnel()
    }

    var isOn: Bool { status == .connected }
    var isBusy: Bool { status == .connecting || status == .disconnecting || status == .reasserting }
}

// MARK: - stanje aplikacije

@MainActor
final class AppModel: ObservableObject {
    @Published var account: Account? = Store.accountJSON.flatMap { Account.parse($0) }
    @Published var loggedIn = !Store.link.isEmpty
    @Published var loading = false
    @Published var message: String?
    @Published var selected = Store.selected
    @Published var pings: [Int: Int] = [:]
    @Published var tab = 0
    let vpn = VPNManager()

    init() {
        if loggedIn { Task { await refresh(silent: true) } }
    }

    func login(_ input: String, autoConnect: Bool = false) async {
        guard let link = Api.normalize(input) else {
            message = t("That doesn't look like your access link.", "To nije ispravan pristupni link. Kopiraj ga ponovo ili skeniraj QR kod.")
            return
        }
        loading = true
        defer { loading = false }
        do {
            let data = try await Api.fetch(link)
            guard let acc = Account.parse(data) else { throw URLError(.cannotParseResponse) }
            Store.link = link
            Store.accountJSON = data
            account = acc
            loggedIn = true
            message = nil
            pingAll()
            if autoConnect { await connect() }
        } catch let e as NSError where e.code == 404 {
            message = t("Account not found. Ask support for a new link.", "Nalog nije pronađen. Traži novi link od podrške.")
        } catch {
            message = t("Can't reach the server. Check your internet.", "Ne mogu dohvatiti nalog. Provjeri internet.")
        }
    }

    func refresh(silent: Bool = false) async {
        let link = Store.link
        guard !link.isEmpty else { return }
        do {
            let data = try await Api.fetch(link)
            if let acc = Account.parse(data) {
                Store.accountJSON = data
                account = acc
                pingAll()
            }
        } catch let e as NSError where e.code == 404 {
            message = t("Your access link is no longer valid.", "Tvoj pristupni link više ne važi.")
        } catch {
            if !silent { message = t("Can't refresh account.", "Ne mogu osvježiti nalog.") }
        }
    }

    func pingAll() {
        for s in account?.servers ?? [] where !s.isChain {
            Task {
                let ms = await Api.ping(s.host, s.port)
                pings[s.id] = ms ?? -1
            }
        }
    }

    func currentServer() -> Server? {
        let list = account?.servers ?? []
        if let s = list.first(where: { $0.id == selected }) { return s }
        let plain = list.filter { !$0.isChain }
        let ok = plain.filter { (pings[$0.id] ?? 9999) >= 0 }
        return ok.min(by: { (pings[$0.id] ?? 9999) < (pings[$1.id] ?? 9999) }) ?? plain.first ?? list.first
    }

    func connect() async {
        guard let acc = account else { return }
        guard acc.canConnect else {
            switch acc.status {
            case "expired", "grace": message = t("Your subscription has expired.", "Pretplata je istekla. Produži je da se možeš spojiti.")
            case "limited": message = t("Your data limit is used up.", "Potrošio si sav promet.")
            case "devices": message = t("Too many devices. Try again in 15 minutes.", "Previše uređaja. Pokušaj ponovo za 15 minuta.")
            default: message = t("Your account is disabled.", "Nalog je ugašen. Javi se podršci.")
            }
            return
        }
        guard let s = currentServer() else {
            message = t("No servers available.", "Trenutno nema dostupnih servera.")
            return
        }
        do {
            try await vpn.connect(config: ConfigBuilder.build(s), brand: acc.brand)
        } catch {
            message = t("Can't start VPN: ", "Ne mogu pokrenuti VPN: ") + error.localizedDescription
        }
    }

    func toggle() async {
        if vpn.isOn || vpn.status == .connecting { vpn.disconnect() } else { await connect() }
    }

    func select(_ id: Int) {
        selected = id
        Store.selected = id
        if vpn.isOn, let s = currentServer() { vpn.reload(config: ConfigBuilder.build(s)) }
    }

    func logout() {
        vpn.disconnect()
        Store.logout()
        account = nil
        loggedIn = false
        selected = -1
    }
}
