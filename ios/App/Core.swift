import Foundation
import Network
import SwiftUI

// MARK: - konstante i pomoćne

enum AppConst {
    static let appGroup = "group.com.veltrix.vpn"
    static let tunnelBundle = "com.veltrix.vpn.tunnel"
    /** adresa panela za probni nalog (dok kupac nema link) */
    static let panelURL = "http://178.104.237.74"
    static var build: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1" }
}

/** Tekst na bosanskom ili engleskom, prema jeziku telefona. */
func t(_ en: String, _ bs: String) -> String {
    let lang = Locale.preferredLanguages.first?.prefix(2).lowercased() ?? "en"
    return ["bs", "hr", "sr", "sh", "me"].contains(String(lang)) ? bs : en
}

func flagOf(_ cc: String) -> String {
    let c = cc.uppercased()
    guard c.count == 2, c.allSatisfy({ $0 >= "A" && $0 <= "Z" }) else { return "🌐" }
    return c.unicodeScalars.compactMap { UnicodeScalar(127397 + $0.value) }.map { String($0) }.joined()
}

func fmtBytes(_ b: Int64) -> String {
    let d = Double(b)
    if d >= 1_073_741_824 { return String(format: "%.1f GB", d / 1_073_741_824) }
    if d >= 1_048_576 { return String(format: "%.0f MB", d / 1_048_576) }
    return String(format: "%.0f KB", d / 1024)
}

extension Color {
    static let bg = Color(red: 0.039, green: 0.039, blue: 0.051)
    static let card = Color(red: 0.082, green: 0.082, blue: 0.110)
    static let card2 = Color(red: 0.110, green: 0.110, blue: 0.145)
    static let line = Color.white.opacity(0.08)
    static let vred = Color(red: 0.890, green: 0.090, blue: 0.169)
    static let vredHi = Color(red: 1.0, green: 0.290, blue: 0.353)
    static let soft = Color(white: 0.75)
    static let muted = Color(white: 0.58)
    static let vgreen = Color(red: 0.24, green: 0.86, blue: 0.52)
}

// MARK: - lokalno spremljeno (dijeli se sa VPN tunelom)

enum Store {
    static let d = UserDefaults(suiteName: AppConst.appGroup) ?? .standard
    static var link: String {
        get { d.string(forKey: "link") ?? "" }
        set { d.set(newValue, forKey: "link") }
    }
    static var accountJSON: Data? {
        get { d.data(forKey: "account") }
        set { d.set(newValue, forKey: "account") }
    }
    /** -1 = automatski */
    static var selected: Int {
        get { d.object(forKey: "server") as? Int ?? -1 }
        set { d.set(newValue, forKey: "server") }
    }
    static func logout() {
        ["link", "account", "server"].forEach { d.removeObject(forKey: $0) }
    }
}

// MARK: - podaci naloga (/s/TOKEN/app)

struct Server: Identifiable {
    let id: Int
    let name: String
    let city: String
    let country: String
    let host: String
    let port: Int
    let outbounds: [[String: Any]]
    let kind: String
    let tags: [String]
    var isChain: Bool { kind != "server" }
    var flag: String { flagOf(country) }
    var countryName: String {
        let loc = Locale(identifier: t("en", "bs"))
        return loc.localizedString(forRegionCode: country) ?? name
    }
}

struct Account {
    let brand: String
    let support: String
    let announce: String
    let username: String
    let plan: String
    let status: String
    let canConnect: Bool
    let expiresAt: Double
    let trafficUsed: Int64
    let trafficLimit: Int64
    let devices: Int
    let devicesActive: Int
    let servers: [Server]
    let link: String

    static func parse(_ data: Data) -> Account? {
        guard let o = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return nil }
        let arr = o["servers"] as? [[String: Any]] ?? []
        let servers: [Server] = arr.map { s in
            Server(
                id: (s["id"] as? NSNumber)?.intValue ?? 0, name: s["name"] as? String ?? "", city: s["city"] as? String ?? "",
                country: s["country"] as? String ?? "", host: s["host"] as? String ?? "", port: (s["port"] as? NSNumber)?.intValue ?? 443,
                outbounds: s["outbounds"] as? [[String: Any]] ?? [], kind: (s["kind"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "server",
                tags: s["tags"] as? [String] ?? []
            )
        }
        func i64(_ k: String) -> Int64 { (o[k] as? NSNumber)?.int64Value ?? 0 }
        return Account(
            brand: o["brand"] as? String ?? "AdiLink VPN", support: o["support"] as? String ?? "", announce: o["announce"] as? String ?? "",
            username: o["username"] as? String ?? "", plan: o["plan"] as? String ?? "", status: o["status"] as? String ?? "",
            canConnect: (o["can_connect"] as? NSNumber)?.boolValue ?? false, expiresAt: (o["expires_at"] as? NSNumber)?.doubleValue ?? 0,
            trafficUsed: i64("traffic_used"), trafficLimit: i64("traffic_limit"), devices: (o["devices"] as? NSNumber)?.intValue ?? 1,
            devicesActive: (o["devices_active"] as? NSNumber)?.intValue ?? 0, servers: servers, link: o["link"] as? String ?? ""
        )
    }
}

func statusText(_ st: String) -> String {
    switch st {
    case "active", "expiring": return t("Active", "Aktivan")
    case "grace": return t("Expired, grace period", "Istekao, grace period")
    case "expired": return t("Expired", "Istekao")
    case "limited": return t("Data limit used up", "Potrošen promet")
    case "devices": return t("Too many devices", "Previše uređaja")
    default: return t("Disabled", "Ugašen")
    }
}

// MARK: - panel

enum Api {
    /** Iz bilo čega što kupac zalijepi (link, veltrix://import?url=…) napravi pretplatni link. */
    static func normalize(_ input: String) -> String? {
        var s = input.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.lowercased().hasPrefix("veltrix://"), let c = URLComponents(string: s), let u = c.queryItems?.first(where: { $0.name == "url" })?.value {
            s = u
        }
        guard let r = s.range(of: "https?://[^\\s]+?/s/[A-Za-z0-9_-]{8,40}", options: .regularExpression) else { return nil }
        return String(s[r])
    }

    static func get(_ url: String) async throws -> Data {
        guard let u = URL(string: url) else { throw URLError(.badURL) }
        var req = URLRequest(url: u, timeoutInterval: 15)
        req.setValue("VeltrixVPN/iOS/\(AppConst.build)", forHTTPHeaderField: "User-Agent")
        let (data, resp) = try await URLSession.shared.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        if code == 404 { throw NSError(domain: "veltrix", code: 404) }
        if code != 200 { throw NSError(domain: "veltrix", code: code, userInfo: [NSLocalizedDescriptionKey: "HTTP \(code)"]) }
        return data
    }

    static func fetch(_ link: String) async throws -> Data { try await get(link + "/app") }

    /** TCP vrijeme spajanja na server (ms), nil ako ne odgovara. */
    static func ping(_ host: String, _ port: Int) async -> Int? {
        await withCheckedContinuation { cont in
            let start = Date()
            let conn = NWConnectionBox(host: host, port: port)
            conn.start { ok in
                cont.resume(returning: ok ? max(1, Int(Date().timeIntervalSince(start) * 1000)) : nil)
            }
        }
    }
}

/** Jedno TCP spajanje sa rokom od 4 sekunde (za ping lokacija). */
final class NWConnectionBox {
    private let conn: NWConnection
    private var done = false
    init(host: String, port: Int) {
        conn = NWConnection(host: NWEndpoint.Host(host), port: NWEndpoint.Port(integerLiteral: UInt16(clamping: port)), using: .tcp)
    }
    func start(_ result: @escaping (Bool) -> Void) {
        let q = DispatchQueue(label: "veltrix.ping")
        let finish: (Bool) -> Void = { [self] ok in
            q.async {
                if self.done { return }
                self.done = true
                self.conn.cancel()
                result(ok)
            }
        }
        conn.stateUpdateHandler = { st in
            switch st {
            case .ready: finish(true)
            case .failed, .cancelled: finish(false)
            default: break
            }
        }
        conn.start(queue: q)
        q.asyncAfter(deadline: .now() + 4) { finish(false) }
    }
}
