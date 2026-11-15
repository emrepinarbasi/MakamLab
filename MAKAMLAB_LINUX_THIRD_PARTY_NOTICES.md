# MakamLab V1.1 — Ubuntu lisans ve üçüncü taraf bildirimleri

MakamLab, Prof. Dr. Emre PINARBAŞI tarafından geliştirilmiştir.
İletişim: `emrepinarbasi@gmail.com`. Copyright © 2026.

MakamLab, **GPL-3.0-or-later** koşulları altında dağıtılır. Tam lisans metni
paketteki `legal/LICENSE.txt` dosyasındadır.

| Bileşen | Lisans / dağıtım biçimi |
|---|---|
| MakamLab V1.1 | GPL-3.0-or-later |
| MakamBox v1.0 kuram ve referans verileri | GPL-3.0-or-later |
| JLayer 1.0.1 | LGPL-2.1 |
| Apache POI / POI OOXML ve bağımlılıkları | Apache-2.0 |
| curvesapi 1.08 | BSD-3-Clause |
| Temurin OpenJDK Runtime 21 | GPL-2.0 with Classpath Exception |
| FFmpeg | Ubuntu sistem paketi; paket lisansı Ubuntu sürümüne göre incelenmelidir |

Ubuntu paketi Temurin OpenJDK 21 çalışma zamanını içerir; çalışma zamanının
kendi `legal` dizini paket içinde korunur. FFmpeg ikili dosyası MakamLab DEB
dosyasına gömülmez. MakamLab, kullanıcının Ubuntu deposundan kurulan `ffmpeg`
komutunu ayrı bir süreç olarak yalnız sıkıştırılmış sesleri PCM çalışma
kopyasına dönüştürmek için çağırır.

MakamBox Studio'nun NeuralNote/MuScriptor, LilyPond ve transkripsiyon
bileşenleri bu MakamLab dağıtımının parçası değildir.
