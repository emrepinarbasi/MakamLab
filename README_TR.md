# MakamLab 1.1.3

![MakamLab yazı logosu](assets/MakamLab-Wordmark-v1.png)

[English README](README.md) · [Kurulum](INSTALLATION.md) ·
[Kaynaktan derleme](BUILDING.md) · [Sürüm notları](RELEASE_NOTES_1.1.3.md)

MakamLab, Türk makam müziği ses kayıtlarındaki perde, aralık, ezgi yönü ve
glissando kullanımlarını incelemek için geliştirilmiş akademik masaüstü
uygulamasıdır. Ölçümler, kullanıcı tarafından onaylanan karar frekansına göre
hesaplanır ve hem cent (`C`) hem de Holderian koma
(`Hc`; `1 Hc = 1200/53 C`) cinsinden raporlanır.

MakamLab otomatik makam teşhisi yapan bir sistem değildir. Kuramsal benzerlik
sonuçları; seyir, kalış, güçlü, çeşni, geçki, kayıt niteliği ve araştırma
tasarımıyla birlikte değerlendirilmelidir.

## Analiz bölümleri

1. **Perde Analizi** — tek bir perdeyi veya iki sınır perdesi arasındaki açık
   aralığı inceler. Perdeler arası analizde sınırların kendisi dışarıda tutulur.
2. **İnici–Çıkıcı Perde Analizi** — kabul edilen hedef perde olaylarını çıkıcı,
   inici ve kalış gruplarında ayrı değerlendirir.
3. **Artık Aralık Analizi** — dört dereceli hareketi ve ikinci–üçüncü dereceler
   arasındaki artık aralığı iki yönde ölçer.
4. **Glisendo/Portamento Analizi** — seçilen iki perde arasındaki çıkıcı ve inici
   glissandoları süre, genişlik, başlangıç/bitiş konumu, biçim ve güven
   değerleriyle raporlar.

## Çalışma ilkeleri

- WAV, AIFF, AU, MP3 ve M4A desteği
- dalga görünümünden kullanıcı seçimiyle karar belirleme veya son kararlı ses
  adayını kullanma
- anti-alias süzgeçli 22,05 kHz çalışma kopyası
- YIN tabanlı temel frekans izleme
- 53-Hc kuramsal referans cetveli
- genel perde envanteri ve dalga biçimiyle doğrulanan karara inişler için ayrı ilk dört derece benzerliği
- frekans, göreli C/Hc, sapma, yön, süre, kararlılık ve güven sütunları
- aynı perde sınıfının güvenilir oktav/register kanıtlarını seçilen kuramsal
  aralığa hizalama; fiziksel register bilgisini denetim için koruma
- güçlü, koşullu ölçülebilir ve yalnız denetim amaçlı olay katmanları
- tablo, grafik, yöntem açıklaması ve sınırlılık içeren DOCX raporları
- Türkçe ve İngilizce arayüz/rapor; makam ve perde özel adları çevrilmez

Varsayılan ilk-dört-ses karşılaştırma kümesi **Çargâh, Kürdî, Rast, Uşşak ve
Hicaz** profillerinden oluşur. Sabâ, Segâh, Müstear, Hüzzam, Nikriz, Pençgâh,
Ferahnâk ve Nişabur profilleri isteğe bağlıdır. Bûselik, 1.1.0 sürümünde bir
karşılaştırma satırı değildir; perde ve aralık sınırı adı olarak kullanılmaya
devam eder.

## Desteklenen sistemler

- macOS 13.5 veya üzeri, Universal (Apple Silicon ve Intel)
- Windows 10/11, 64 bit (`x64`)
- Ubuntu 22.04 LTS veya üzeri, Intel/AMD 64 bit (`amd64`)
- uzun kayıtlar için en az 8 GB RAM önerilir

Java 21 kurulum paketlerinin içindedir. Ubuntu paketi FFmpeg ile gerekli masaüstü
kitaplıklarını sistem paket yöneticisi üzerinden kurar.

> **İmza uyarısı:** 1.1.3 topluluk paketleri ticari kod imzasına sahip değildir.
> macOS paketi ad-hoc imzalıdır ve noter onaylı değildir; Windows kurucu ve
> portable paketleri imzasızdır. Bu nedenle işletim sistemi güvenlik uyarısı
> gösterebilir. Kullanmadan önce SHA-256 değerini doğrulayın.

## Gizlilik

Ses analizi ve rapor üretimi bilgisayarda yerel olarak yapılır. MakamLab ses
kayıtlarını yüklemez, hesap gerektirmez ve telemetri içermez. Yalnızca
kullanıcının belge veya e-posta bağlantısını açması dış uygulamaya yönlendirir.

## Atıf

Depodaki `CITATION.cff` dosyasının etkinleştirdiği **Cite this repository**
menüsünü kullanabilir veya yazılımı şöyle gösterebilirsiniz:

> Pınarbaşı, E. (2026). *MakamLab* (Sürüm 1.1.3) [Bilgisayar yazılımı].

## Lisans ve köken

MakamLab, **Prof. Dr. Emre PINARBAŞI** tarafından geliştirilmiş ve
`GPL-3.0-or-later` ile yayımlanmıştır. Bilge Miraç Atıcı'nın GPL lisanslı
MakamBox projesinden türetilmiş kuram ve referans verilerini kullanır. Ayrıntılı
bilgi için `THIRD_PARTY_NOTICES.md`, `SBOM.spdx.json` ve `licenses/` dizinine
bakın.

İletişim: [emrepinarbasi@gmail.com](mailto:emrepinarbasi@gmail.com)
