import Combine
import NetworkExtension
import SwiftUI
import UIKit

@main
struct VeltrixApp: App {
    @StateObject private var model = AppModel()
    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .environmentObject(model.vpn)
                .preferredColorScheme(.dark)
                .tint(.vred)
                // veltrix://import?url=… iz e-maila ili sa stranice naloga: uvezi i spoji
                .onOpenURL { url in
                    Task { await model.login(url.absoluteString, autoConnect: true) }
                }
        }
    }
}

// MARK: - logo (štit sa V, iz SVG-a 200×224)

struct ShieldLogo: View {
    var size: CGFloat = 80
    var body: some View {
        Canvas { ctx, sz in
            let sx = sz.width / 200, sy = sz.height / 224
            func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * sx, y: y * sy) }
            func poly(_ pts: [(CGFloat, CGFloat)]) -> Path {
                var path = Path()
                path.move(to: pt(pts[0].0, pts[0].1))
                for q in pts.dropFirst() { path.addLine(to: pt(q.0, q.1)) }
                path.closeSubpath()
                return path
            }
            // M100 4 L190 38 L190 102 C190 158 154 200 100 220 C46 200 10 158 10 102 L10 38 Z
            var outer = Path()
            outer.move(to: pt(100, 4)); outer.addLine(to: pt(190, 38)); outer.addLine(to: pt(190, 102))
            outer.addCurve(to: pt(100, 220), control1: pt(190, 158), control2: pt(154, 200))
            outer.addCurve(to: pt(10, 102), control1: pt(46, 200), control2: pt(10, 158))
            outer.addLine(to: pt(10, 38)); outer.closeSubpath()
            // M100 22 L174 50 L174 102 C174 148 145 182 100 199 C55 182 26 148 26 102 L26 50 Z
            var inner = Path()
            inner.move(to: pt(100, 22)); inner.addLine(to: pt(174, 50)); inner.addLine(to: pt(174, 102))
            inner.addCurve(to: pt(100, 199), control1: pt(174, 148), control2: pt(145, 182))
            inner.addCurve(to: pt(26, 102), control1: pt(55, 182), control2: pt(26, 148))
            inner.addLine(to: pt(26, 50)); inner.closeSubpath()
            ctx.fill(outer, with: .color(.vred))
            ctx.fill(inner, with: .color(Color(red: 0.07, green: 0.07, blue: 0.086)))
            ctx.fill(poly([(48, 70), (80, 70), (100, 138), (100, 184)]), with: .color(.vred))
            ctx.fill(poly([(152, 70), (120, 70), (100, 138), (100, 184)]), with: .color(Color(red: 0.62, green: 0.05, blue: 0.10)))
            ctx.fill(poly([(86, 58), (114, 58), (100, 96)]), with: .color(.vredHi))
        }
        .frame(width: size, height: size * 1.12)
    }
}

struct BigButton: View {
    let title: String
    var filled = true
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(title).font(.system(size: 17, weight: .semibold)).frame(maxWidth: .infinity).frame(height: 54)
                .foregroundColor(.white)
                .background(filled ? Color.vred : Color.card)
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(filled ? Color.clear : Color.line, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 14))
        }
    }
}

struct CardBox<Content: View>: View {
    @ViewBuilder let content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .background(Color.card)
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.line, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

// MARK: - okvir

struct RootView: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        ZStack {
            Color.bg.ignoresSafeArea()
            RadialGradient(colors: [Color.vred.opacity(0.22), .clear], center: .top, startRadius: 0, endRadius: 520).ignoresSafeArea()
            if model.loggedIn {
                TabView(selection: $model.tab) {
                    HomeView().tabItem { Label(t("Home", "Početna"), systemImage: "house.fill") }.tag(0)
                    LocationsView().tabItem { Label(t("Locations", "Lokacije"), systemImage: "mappin.and.ellipse") }.tag(1)
                    AccountView().tabItem { Label(t("Account", "Nalog"), systemImage: "person.fill") }.tag(2)
                }
            } else {
                LoginView()
            }
        }
        .alert(model.message ?? "", isPresented: Binding(get: { model.message != nil }, set: { if !$0 { model.message = nil } })) {
            Button("OK", role: .cancel) {}
        }
    }
}

// MARK: - prijava

struct LoginView: View {
    @EnvironmentObject var model: AppModel
    @State private var input = ""
    @State private var scanning = false
    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                Spacer(minLength: 40)
                ShieldLogo(size: 96)
                VStack(spacing: 4) {
                    Text("VELTRIX").font(.system(size: 34, weight: .black)).kerning(3)
                    Text("V P N").font(.system(size: 14, weight: .semibold)).foregroundColor(.vredHi).kerning(4)
                }
                Text(t("Scan the QR code or paste the access link you received after purchase.", "Skeniraj QR kod ili zalijepi pristupni link koji si dobio nakon kupovine."))
                    .font(.system(size: 15)).foregroundColor(.soft).multilineTextAlignment(.center).padding(.horizontal, 8)
                BigButton(title: t("Scan QR code", "Skeniraj QR kod")) { scanning = true }
                Text(t("or", "ili")).foregroundColor(.muted)
                HStack {
                    TextField(t("Access link (https://…/s/…)", "Pristupni link (https://…/s/…)"), text: $input)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                    Button { input = UIPasteboard.general.string ?? "" } label: { Image(systemName: "doc.on.clipboard").foregroundColor(.soft) }
                }
                .padding(.horizontal, 16).frame(height: 54).background(Color.card)
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.line, lineWidth: 1)).clipShape(RoundedRectangle(cornerRadius: 14))
                if model.loading {
                    ProgressView().tint(.vred).frame(height: 54)
                } else {
                    BigButton(title: t("Log in", "Prijavi se"), filled: false) { Task { await model.login(input) } }
                }
                Text(t("Don't have an account? Contact the seller you bought from.", "Nemaš nalog? Javi se prodavcu od kojeg kupuješ."))
                    .font(.system(size: 13)).foregroundColor(.muted).multilineTextAlignment(.center).padding(.top, 6)
            }
            .padding(24)
        }
        .sheet(isPresented: $scanning) {
            QRScannerView { code in
                scanning = false
                Task { await model.login(code) }
            }
            .ignoresSafeArea()
        }
    }
}

// MARK: - početna

struct HomeView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var vpn: VPNManager
    @State private var now = Date()
    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        let on = vpn.isOn
        let server = model.currentServer()
        ScrollView {
            VStack(spacing: 22) {
                HStack {
                    ShieldLogo(size: 30)
                    Text(model.account?.brand ?? "Veltrix VPN").font(.system(size: 20, weight: .bold))
                    Spacer()
                    Button { Task { await model.refresh() } } label: { Image(systemName: "arrow.clockwise").foregroundColor(.soft) }
                }
                Button { Task { await model.toggle() } } label: {
                    ZStack {
                        Circle().fill(Color.card).frame(width: 210, height: 210)
                        Circle().stroke(on ? Color.vred : Color.line, lineWidth: 6).frame(width: 210, height: 210)
                            .shadow(color: on ? Color.vred.opacity(0.6) : .clear, radius: 24)
                        if vpn.isBusy {
                            ProgressView().scaleEffect(1.8).tint(.vred)
                        } else {
                            Image(systemName: "power").font(.system(size: 64, weight: .bold)).foregroundColor(on ? .vred : .soft)
                        }
                    }
                }
                .buttonStyle(.plain).padding(.top, 10)
                VStack(spacing: 6) {
                    Text(on ? t("Protected", "Zaštićen") : vpn.isBusy ? t("Please wait…", "Sačekaj…") : t("Not protected", "Nije zaštićen"))
                        .font(.system(size: 24, weight: .bold)).foregroundColor(on ? .vgreen : .white)
                    if on, let at = vpn.connectedAt {
                        let secs = max(0, Int(now.timeIntervalSince(at)))
                        Text(String(format: "%02d:%02d:%02d", secs / 3600, secs % 3600 / 60, secs % 60)).font(.system(size: 15, design: .monospaced)).foregroundColor(.soft)
                    }
                }
                Button { model.tab = 1 } label: {
                    HStack(spacing: 12) {
                        Text(server?.flag ?? "🌐").font(.system(size: 30))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(model.selected == -1 ? t("Fastest location", "Najbrža lokacija") : t("Location", "Lokacija")).font(.system(size: 13)).foregroundColor(.muted)
                            Text(server.map { $0.isChain ? $0.name : "\($0.countryName) · \($0.city)" } ?? "—").font(.system(size: 17, weight: .semibold)).foregroundColor(.white)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").foregroundColor(.muted)
                    }
                    .padding(16).background(Color.card).overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.line, lineWidth: 1)).clipShape(RoundedRectangle(cornerRadius: 16))
                }
                BigButton(title: on ? t("Disconnect", "Prekini vezu") : t("Connect", "Spoji se")) { Task { await model.toggle() } }
                if let a = model.account?.announce, !a.isEmpty {
                    Text(a).font(.system(size: 14)).foregroundColor(Color(red: 0.96, green: 0.65, blue: 0.14)).frame(maxWidth: .infinity, alignment: .leading)
                        .padding(14).background(Color.card).clipShape(RoundedRectangle(cornerRadius: 14))
                }
            }
            .padding(20)
        }
        .onReceive(timer) { now = $0 }
        .refreshable { await model.refresh() }
    }
}

// MARK: - lokacije

struct LocationsView: View {
    @EnvironmentObject var model: AppModel
    @State private var query = ""
    var body: some View {
        let all = model.account?.servers ?? []
        let list = all.filter { query.isEmpty || "\($0.name) \($0.city) \($0.countryName)".localizedCaseInsensitiveContains(query) }
            .sorted { (model.pings[$0.id].flatMap { $0 >= 0 ? $0 : nil } ?? 99999) < (model.pings[$1.id].flatMap { $0 >= 0 ? $0 : nil } ?? 99999) }
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(t("Locations", "Lokacije")).font(.system(size: 26, weight: .bold))
                HStack {
                    Image(systemName: "magnifyingglass").foregroundColor(.muted)
                    TextField(t("Search", "Pretraži"), text: $query)
                }
                .padding(.horizontal, 14).frame(height: 46).background(Color.card).clipShape(RoundedRectangle(cornerRadius: 12))
                CardBox {
                    row(id: -1, flag: "⚡️", title: t("Fastest location", "Najbrža lokacija"), sub: t("Picked automatically", "Bira se automatski"), ping: nil)
                    ForEach(list) { s in
                        Divider().background(Color.line)
                        row(id: s.id, flag: s.flag, title: s.isChain ? s.name : s.countryName, sub: s.isChain ? s.kind.capitalized : s.city + (s.tags.isEmpty ? "" : " · " + s.tags.joined(separator: " · ").uppercased()), ping: model.pings[s.id])
                    }
                }
            }
            .padding(20)
        }
        .refreshable { await model.refresh() }
    }

    @ViewBuilder
    private func row(id: Int, flag: String, title: String, sub: String, ping: Int?) -> some View {
        Button {
            model.select(id)
            model.tab = 0
        } label: {
            HStack(spacing: 12) {
                Text(flag).font(.system(size: 28))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.system(size: 16, weight: .semibold)).foregroundColor(.white)
                    Text(sub).font(.system(size: 12)).foregroundColor(.muted)
                }
                Spacer()
                if let p = ping { Text(p >= 0 ? "\(p) ms" : "—").font(.system(size: 13)).foregroundColor(p >= 0 && p < 120 ? .vgreen : .muted) }
                if model.selected == id { Image(systemName: "checkmark.circle.fill").foregroundColor(.vred) }
            }
            .padding(.horizontal, 16).padding(.vertical, 12)
        }
    }
}

// MARK: - nalog

struct AccountView: View {
    @EnvironmentObject var model: AppModel
    @State private var confirmLogout = false
    var body: some View {
        let a = model.account
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(t("My Account", "Moj nalog")).font(.system(size: 26, weight: .bold))
                HStack(spacing: 14) {
                    ShieldLogo(size: 52)
                    VStack(alignment: .leading, spacing: 4) {
                        Text((a?.plan.isEmpty == false ? a!.plan : "Premium").uppercased()).font(.system(size: 19, weight: .bold))
                        Text(statusText(a?.status ?? "")).foregroundColor(a?.canConnect == true ? .vgreen : .vredHi)
                        if let e = a?.expiresAt, e > 0 {
                            Text(t("Valid until ", "Vrijedi do ") + Date(timeIntervalSince1970: e / 1000).formatted(date: .long, time: .omitted)).font(.system(size: 13)).foregroundColor(.soft)
                        }
                    }
                }
                .padding(18).frame(maxWidth: .infinity, alignment: .leading).background(Color.card)
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.vred.opacity(0.5), lineWidth: 1)).clipShape(RoundedRectangle(cornerRadius: 16))
                CardBox {
                    info("person.fill", t("User", "Korisnik"), a?.username ?? "—")
                    Divider().background(Color.line)
                    info("arrow.down.circle", t("Data", "Promet"), (a?.trafficLimit ?? 0) > 0 ? "\(fmtBytes(a!.trafficUsed)) / \(fmtBytes(a!.trafficLimit))" : fmtBytes(a?.trafficUsed ?? 0) + " · ∞")
                    Divider().background(Color.line)
                    info("iphone", t("Devices", "Uređaji"), "\(a?.devicesActive ?? 0) / \(a?.devices ?? 1)")
                }
                CardBox {
                    action("creditcard", t("Renew / my account page", "Produži / stranica naloga")) {
                        if let u = URL(string: Store.link) { UIApplication.shared.open(u) }
                    }
                    if let s = a?.support, !s.isEmpty {
                        Divider().background(Color.line)
                        action("questionmark.bubble", t("Help & Support", "Podrška") + " · " + s) {
                            let tg = s.range(of: "@[A-Za-z0-9_]{4,}", options: .regularExpression).map { String(s[$0].dropFirst()) }
                            let url = s.hasPrefix("http") ? s : tg.map { "https://t.me/\($0)" }
                            if let url, let u = URL(string: url) { UIApplication.shared.open(u) }
                        }
                    }
                    Divider().background(Color.line)
                    action("rectangle.portrait.and.arrow.right", t("Log out", "Odjava")) { confirmLogout = true }
                }
                Text("Veltrix VPN · iOS build \(AppConst.build) · sing-box (GPLv3)").font(.system(size: 12)).foregroundColor(.muted)
            }
            .padding(20)
        }
        .refreshable { await model.refresh() }
        .confirmationDialog(t("Log out?", "Odjaviti se?"), isPresented: $confirmLogout, titleVisibility: .visible) {
            Button(t("Log out", "Odjava"), role: .destructive) { model.logout() }
        }
    }

    private func info(_ icon: String, _ k: String, _ v: String) -> some View {
        HStack {
            Image(systemName: icon).foregroundColor(.vred).frame(width: 26)
            Text(k).foregroundColor(.soft)
            Spacer()
            Text(v).fontWeight(.semibold)
        }
        .padding(.horizontal, 16).padding(.vertical, 14)
    }

    private func action(_ icon: String, _ title: String, _ run: @escaping () -> Void) -> some View {
        Button(action: run) {
            HStack {
                Image(systemName: icon).foregroundColor(.vred).frame(width: 26)
                Text(title).foregroundColor(.white)
                Spacer()
                Image(systemName: "chevron.right").foregroundColor(.muted)
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
        }
    }
}
