# MakamLab 1.1.1

Bu bakım sürümü, perdeler arası analizde farklı fiziksel oktavlarda icra edilen
aynı perde sınıflarının birlikte değerlendirilmesini düzeltir.

## Analiz düzeltmeleri

- Perde olayları, karar sesinin seçildiği fiziksel oktavdan bağımsız olarak
  aynı perde sınıfına katlanır.
- Karar frekansının bir veya iki oktav değiştirilmesi temas sayısını,
  ölçülebilir olayları ve normalize aralık konumlarını artık değiştirmez.
- Aynı perde sınıfındaki farklı fiziksel oktavlar tek bir olay halinde yanlış
  birleştirilmez; zaman sıraları ve ayrı icra olayları korunur.
- İlk dört ses benzerliğinde 2., 3. ve 4. derecelerin tamamına ait kanıt yoksa
  yanıltıcı bir yüzde yerine eksik kanıt işareti gösterilir.
- Bûselik, perde adı olarak korunur; ilk-dört-ses karşılaştırma adayı olarak
  gösterilmez.

## Uyumluluk ve rapor sözleşmesi

- DOCX rapor başlıkları, grafik adları ve grafik yerleşimleri değiştirilmedi.
- Türkçe ve İngilizce rapor sözleşmeleri aynı kaldı.
- MP3, M4A ve WAV yükleme doğrulamaları üç platform paketinde çalıştırıldı.

## Doğrulama

Gerçek bir kayıt üzerinde karar frekansı 444,3528 Hz, 222,1764 Hz ve
111,0882 Hz seçilerek tekrarlanan testlerin üçünde de 68 temas, 29
ölçülebilir olay ve 2,380 saniye ölçülebilir kanıt elde edildi.

Dağıtımlar:

- macOS Apple Silicon DMG
- Windows 10/11 x64 kurulum EXE'si (imzasız test dağıtımı)
- Ubuntu amd64 DEB
