// Prilagođeno iz sing-box-for-apple (GPLv3), verzija 1.13.21: VPN tunel koji pokreće sing-box.
import Foundation
import Libbox
import NetworkExtension

struct TunnelError: LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}

final class PacketTunnelProvider: NEPacketTunnelProvider {
    static let appGroup = "group.com.veltrix.vpn"

    private(set) var commandServer: LibboxCommandServer?
    private lazy var platformInterface = PlatformInterface(self)
    private var configContent: String?

    private var sharedDir: URL {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: Self.appGroup)
            ?? FileManager.default.temporaryDirectory
    }

    override func startTunnel(options startOptions: [String: NSObject]?) async throws {
        let base = sharedDir
        let working = base.appendingPathComponent("Working", isDirectory: true)
        let temp = base.appendingPathComponent("Temp", isDirectory: true)
        try? FileManager.default.createDirectory(at: working, withIntermediateDirectories: true)
        try? FileManager.default.createDirectory(at: temp, withIntermediateDirectories: true)

        // konfiguraciju šalje aplikacija; ako se tunel upali sam (npr. "uvijek uključen"), uzmi zadnju sačuvanu
        let saved = base.appendingPathComponent("config.json")
        if let c = startOptions?["configContent"] as? String {
            configContent = c
            try? c.write(to: saved, atomically: true, encoding: .utf8)
        } else {
            configContent = try? String(contentsOf: saved, encoding: .utf8)
        }
        guard configContent != nil else { throw TunnelError("Nema konfiguracije. Otvori aplikaciju i spoji se.") }

        let options = LibboxSetupOptions()
        options.basePath = base.path
        options.workingPath = working.path
        options.tempPath = temp.path
        options.logMaxLines = 3000
        var setupError: NSError?
        LibboxSetup(options, &setupError)
        if let setupError { throw TunnelError("setup: \(setupError.localizedDescription)") }

        var stderrError: NSError?
        LibboxRedirectStderr(temp.appendingPathComponent("stderr.log").path, &stderrError)
        LibboxSetMemoryLimit(true)

        var error: NSError?
        commandServer = LibboxNewCommandServer(platformInterface, platformInterface, &error)
        if let error { throw TunnelError("command server: \(error.localizedDescription)") }
        do {
            try commandServer!.start()
        } catch {
            throw TunnelError("command server start: \(error.localizedDescription)")
        }
        try await startService()
    }

    private func startService() async throws {
        guard let configContent else { throw TunnelError("Nema konfiguracije.") }
        do {
            try commandServer!.startOrReloadService(configContent, options: LibboxOverrideOptions())
        } catch {
            throw TunnelError("VPN motor: \(error.localizedDescription)")
        }
    }

    func writeMessage(_ message: String) {
        commandServer?.writeMessage(2, message: message)
    }

    func stopService() {
        do {
            try commandServer?.closeService()
        } catch {
            writeMessage("stop: \(error.localizedDescription)")
        }
        platformInterface.reset()
    }

    func reloadService() async throws {
        reasserting = true
        defer { reasserting = false }
        try await startService()
    }

    override func stopTunnel(with reason: NEProviderStopReason) async {
        stopService()
        if let server = commandServer {
            try? await Task.sleep(nanoseconds: 100 * NSEC_PER_MSEC)
            server.close()
            commandServer = nil
        }
    }

    // aplikacija šalje novu konfiguraciju (promjena lokacije) bez gašenja tunela
    override func handleAppMessage(_ messageData: Data) async -> Data? {
        guard let c = String(data: messageData, encoding: .utf8), !c.isEmpty else { return nil }
        configContent = c
        try? c.write(to: sharedDir.appendingPathComponent("config.json"), atomically: true, encoding: .utf8)
        do {
            try await reloadService()
            return nil
        } catch {
            return error.localizedDescription.data(using: .utf8)
        }
    }

    override func sleep() async {
        commandServer?.pause()
    }

    override func wake() {
        commandServer?.wake()
    }
}
