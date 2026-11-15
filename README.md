# MakamLab 1.1.0

MakamLab, Türk makam müziği ses kayıtlarında icra edilen perdeleri karar
frekansına göre inceleyen akademik masaüstü uygulamasıdır. Hesaplamalarda cent
(`C`) ve Holderian koma (`Hc`, `1 Hc = 1200/53 C`) birlikte kullanılır.

## Analiz bölümleri

1. **Perde Analizi** — tek perde veya sınır perdeleri dışarıda bırakılan açık
   perde aralığı analizi.
2. **İnici–Çıkıcı Perde Analizi** — Uşşak dörtlüsündeki Segâh kullanımını
   çıkıcı, inici ve kalış gruplarında ayrı inceler.
3. **Artık Aralık Analizi** — Hicaz dörtlüsünün 1–2–3–4 hareketlerini ve
   özellikle 2↔3 artık aralığını yönlere göre ölçer.
4. **Glissando Analizi** — seçilen iki perde arasındaki çıkıcı ve inici
   glissandoları süre, genişlik, başlangıç/bitiş ve güven değerleriyle raporlar.

Uygulama karar sesini dalga görünümündeki kullanıcı seçimiyle veya son kararlı
ses adayıyla belirleyebilir. Sonuçlar olay tabloları, akademik grafikler ve DOCX
raporları olarak sunulur. Arayüz ve raporlar Türkçe ve İngilizce kullanılabilir;
makam ve perde özel adları çevrilmez.

Perde Analizi benzerlik raporu varsayılan olarak Çargâh, Kürdî, Rast, Uşşak
ve Hicaz dizilerinin ilk dört sesini karşılaştırır. Sabâ, Segâh,
Müstear, Hüzzam, Nikriz, Pençgâh, Ferahnâk ve Nişabur profilleri ana
sayfadaki Ayarlar penceresinden ayrı ayrı eklenebilir. Kaydedilen seçim analiz
tablosu, grafik ve DOCX raporunda birlikte kullanılır.

Benzerlik yüzdesi karar sesini ortak ankraj kabul eder ve yalnız ilk dört sesin
ayırt edici 2., 3. ve 4. derecelerindeki gerçek F0 kanıtını puanlar. Grafik,
tablo ve metinsel sonuç aynı yüzdeyi kullanır; sonuç tek başına makam teşhisi
olarak yorumlanmaz.

Perde Analizi ve Perdeler Arası Analiz DOCX raporları, genel perde envanterine
göre benzerlik ile gerçek F0 zaman sırasından çıkarılan sıralı cümle kanıtına
göre benzerliği iki ayrı grafikte sunar. Aynı gerçek kanıt bütün referanslarla
kesintisiz Hc uzaklığı üzerinden karşılaştırılır; bir profil kendi lehine olay
seçemez. Art arda tekrarlar korunur ancak tek bir cümlenin sonucu orantısız
biçimde baskılamaması için ağırlıkları sınırlandırılır. `%80` çizgisi yalnız
görsel güvenilirlik eşiğidir; düşük puanlı sonuçlar denetim amacıyla silinmez.

## Sistem gereksinimleri

- macOS 13.5 veya üzeri, Apple Silicon (arm64)
- Windows 10/11 x64
- Ubuntu 22.04 LTS veya üzeri, Intel/AMD 64 bit (`amd64`)
- Uzun kayıtlar için en az 8 GB RAM önerilir
- WAV, AIFF, AU, MP3 veya M4A ses kaydı

## Kaynaktan derleme

Java 21 JDK, `jpackage`, Python 3 ve macOS paketi için Apple Silicon Mac gerekir.
Windows çapraz paketi Zig, Windows JRE ve FFmpeg bağımlılıklarını doğrulanmış
adreslerinden indirir.

```sh
./build-makam-macos.sh
./build-makam-windows-cross.sh
./build-makam-linux-deb.sh
```

Derleme çıktıları `build/makam/` altında oluşturulur. Bu temiz kaynak
snapshot'ına derleme çıktıları ve geçici dosyalar dahil edilmemiştir.

## Kaynak yapısı

- `src/main/java`: MakamLab uygulama ve analiz kaynakları
- `src/main/resources`: kuram verileri ile Türkçe ve İngilizce dil kaynakları
- `src/test/java`: MakamLab'a özgü regresyon testleri
- `assets`: uygulama simgesi, kelime logosu ve dört bölüm simgesi
- `lib`: DOCX, ses ve yardımcı Java bağımlılıkları
- `tools`: Türkçe/İngilizce dil ve paket doğrulama araçları
- `windows`, `macos`: platform paketleme kaynakları

MakamBox Studio'nun transkripsiyon, NeuralNote/MuScriptor ve LilyPond bileşenleri
bu MakamLab kaynak snapshot'ında bulunmaz.

## Lisans ve atıf

MakamLab, Prof. Dr. Emre PINARBAŞI tarafından geliştirilmiştir ve
`GPL-3.0-or-later` koşullarıyla dağıtılır. Uygulama, MakamBox projesinden
türetilmiş kuram ve referans verilerini kullanır. Ayrıntılı bileşen ve lisans
listesi `MAKAMLAB_THIRD_PARTY_NOTICES.md` dosyasındadır.

İletişim: emrepinarbasi@gmail.com

