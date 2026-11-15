# Installation / Kurulum

Download the package for your operating system from the GitHub **Releases**
page. Confirm its SHA-256 value against `SHA256SUMS.txt` before installation.

GitHub **Releases** sayfasından işletim sisteminize uygun paketi indirin.
Kurulumdan önce dosyanın SHA-256 değerini `SHA256SUMS.txt` ile karşılaştırın.

## macOS — Apple Silicon

Supported package: `MakamLab-V1.1.0-macOS-Apple-Silicon-arm64.dmg`

1. Open the DMG and drag MakamLab to Applications.
2. The package is ad-hoc signed but not Apple-notarized. If Gatekeeper blocks
   the first launch, open **System Settings → Privacy & Security** and confirm
   the explicitly downloaded application.
3. Do not disable Gatekeeper globally.

Paket Apple Developer ID ile imzalanmış/noter onaylı değildir. İlk açılışta
engellenirse **Sistem Ayarları → Gizlilik ve Güvenlik** bölümünde yalnızca
bilerek indirdiğiniz MakamLab için açma onayı verin. Gatekeeper'ı bütünüyle
kapatmayın.

## Windows 10/11 x64

Packages:

- installer: `MakamLab-1.1.0-Windows-x64-Setup-UNSIGNED.exe`
- portable: `MakamLab-1.1.0-Windows-x64-Portable-UNSIGNED.zip`

The files are not Authenticode-signed. Windows SmartScreen may show a warning.
Verify the checksum and source before choosing to run the package. The portable
archive can be extracted to a user-writable directory and started without an
installer.

Dosyalar Authenticode ile imzalı değildir; SmartScreen uyarısı görülebilir.
Çalıştırmadan önce sağlama değerini ve indirme kaynağını doğrulayın. Portable
paket, kullanıcı yazma izni bulunan bir klasöre çıkarılarak kurulumsuz açılır.

## Ubuntu 22.04+ amd64

```sh
sudo apt install ./MakamLab-1.1.0-Ubuntu-amd64.deb
```

The package bundles Java 21 and declares its FFmpeg and desktop-library
dependencies. `apt` installs missing dependencies from the configured Ubuntu
repositories.

Paket Java 21'i içerir; FFmpeg ve masaüstü kitaplığı bağımlılıklarını bildirir.
Eksik bağımlılıklar yapılandırılmış Ubuntu depolarından `apt` tarafından
kurulur.

## Checksum verification / Sağlama doğrulaması

macOS or Linux:

```sh
shasum -a 256 -c SHA256SUMS.txt
```

Windows PowerShell:

```powershell
Get-FileHash .\MakamLab-1.1.0-Windows-x64-Setup-UNSIGNED.exe -Algorithm SHA256
```

Compare the result character-for-character with the corresponding line in
`SHA256SUMS.txt`.
