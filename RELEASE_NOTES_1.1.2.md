# MakamLab 1.1.2

Bu kararlı bakım sürümü, perde analizinde özellikle kısa icra edilen ikinci
derece kanıtlarının korunmasını ve ayrı icra tepelerinin doğru temsilini
iyileştirir. Rapor başlıkları, grafik adları ve sayfa yerleşimi değiştirilmemiştir.

## Analiz düzeltmeleri

- En az üç bağımsız zaman bölgesinde tekrarlanan, toplam süresi en az 0,10
  saniye ve ortalama güveni en az %80 olan 20–60 ms ikinci derece temasları
  genel perde envanterinde korunur.
- Tek bir yerel bölgede oluşan kısa sıçramalar ve düşük güvenli geçişler
  benzerlik yüzdesine yükseltilmez.
- Birkaç geçiş olayıyla birbirine bağlanan iki ayrı icra tepesi artık yapay bir
  orta merkezde birleştirilmez; baskın ve ikincil tepeler ayrı tutulur.
- Bütün dizi/çeşni profilleri aynı sabit gerçek F0 kanıtıyla karşılaştırılmaya
  devam eder. Bir profil kendi lehine farklı olay seçemez.
- Sıralı cümle kanıtı, genel perde envanterinden ayrı değerlendirilir. Yeterli
  kronolojik yol yoksa program yüzdelik uydurmaz ve kısmi/yetersiz kanıt
  uyarısını korur.

## Rapor ve uyumluluk

- DOCX raporunun bölüm sırası, başlıkları, tabloları ve grafik yerleşimleri
  önceki kararlı sürümle aynıdır.
- Türkçe ve İngilizce arayüz ile rapor izolasyonu doğrulanmıştır.
- WAV, MP3 ve M4A ses yolları ile seçili bölgeden karar atama sözleşmesi
  korunmuştur.

## Doğrulama

- 54 çekirdek ve 3 masaüstü arayüz regresyon testi başarıyla tamamlandı.
- Elazığ Devlet Korosu Longa kaydında 59 temas, 23 ölçülebilir olay ve 22
  kararlı olay değişmeden korunurken, daha önce kaybolan ikinci derece kanıtı
  7 bağımsız kısa olay ve 0,14 saniyelik ortak kanıt olarak geri kazanıldı.
- Elazığ Devlet Korosu Peşrev kaydında genel envanter 45 temas ve 16
  ölçülebilir olay üretirken, yeterli kronolojik dörtlü yol bulunmadığı için
  sıralı cümle grafiğinin boş kalması doğrulandı; ayrı kayıt bölümleri yapay
  biçimde tek cümlede birleştirilmedi.

Dağıtımlar:

- macOS 13.5+ Apple Silicon DMG
- Windows 10/11 x64 kurulum EXE'si ve taşınabilir ZIP (imzasız dağıtım)
- Ubuntu 22.04+ amd64 DEB
- Tam kaynak kod ZIP'i
