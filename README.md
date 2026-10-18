# Veltrix VPN (Android)

Aplikacija za kupce Veltrix VPN-a. Radi sa VPN panelom (`maid-cyber/vpn-panel`):
kupac skenira QR kod ili zalijepi svoj pristupni link, a aplikacija sama dobije servere i stanje naloga.

- **Ekrani:** Početna (veliko dugme i štit, brzina, vrijeme veze), Lokacije (zastave, ping, omiljeni, najbrži server), Moj nalog (pretplata, promet, protokol, podrška).
- **Protokoli:** VLESS + Reality i Shadowsocks 2022, preko sing-box motora.
- **Jezik:** bosanski ako je telefon na bs/hr/sr, inače engleski.

## Izgradnja

Svaki push na `main` pokreće GitHub Actions:
1. Provjeri sing-box konfiguraciju.
2. Izgradi sing-box (libbox) iz izvornog koda; prvi put traje ~15–20 min, poslije iz keša.
3. Izgradi APK.

Gotov APK je u **Releases** (i u Actions → Artifacts).

## Licenca

GPLv3. VPN dio (`app/src/main/java/com/veltrix/vpn/bg`) je preuzet i prilagođen iz
[sing-box-for-android](https://github.com/SagerNet/sing-box-for-android) (GPLv3), a motor je
[sing-box](https://github.com/SagerNet/sing-box) (GPLv3). Kod ove aplikacije mora ostati dostupan pod istom licencom.

Probni ključ za potpis (`app/veltrix-test.jks`) je samo za testiranje. Za prave kupce napravi novi ključ i čuvaj ga u GitHub tajnama.

## iPhone (ios/)

SwiftUI aplikacija + Packet Tunnel (sing-box libbox), projekat se pravi XcodeGenom (`ios/project.yml`).
GitHub Actions (`.github/workflows/ios.yml`, macOS) izgradi libbox za iOS i aplikaciju **bez potpisa** (`ios-N` u Releases).
Za instalaciju na iPhone treba potpis sa Apple Developer nalogom (Network Extension dozvola); besplatni nalog ne može pokrenuti VPN tunel.
