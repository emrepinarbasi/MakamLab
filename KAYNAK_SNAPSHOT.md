# Temiz Kaynak Snapshot'ı

- Proje: MakamLab
- Sürüm: 1.1.3
- Snapshot tarihi: 26 Kasım 2026
- Kaynak Java sürümü: 21
- Ana Java kaynak dosyası: 111
- Regresyon testi: 57
- Dil kaynağı: Türkçe ve İngilizce

Bu snapshot, ortak geliştirme ağacındaki MakamLab kodundan hazırlanmıştır.
MakamBox Studio'ya ait transkripsiyon arayüzü ve motorları, NeuralNote,
MuScriptor, LilyPond/Hacklily çalışma zamanı, eski Studio rapor/ayar ekranları,
derleme çıktıları, geçici dosyalar, yedek uygulamalar ve AppleDouble `._*`
dosyaları dahil edilmemiştir.

Temiz kaynak; Java 21 ile baştan derlenmiş, tüm analiz regresyonları, DOCX
raporu, ilk-dört-ses profil ayarları, oynatma denetimleri, lisans penceresi ve
Türkçe/İngilizce dil izolasyon denetimleri başarıyla doğrulanmıştır.

Perde Analizi ve Perdeler Arası Analiz raporlarındaki yönsel ilk-dört-ses
katmanı; aynı derece tekrarları, gerçek yön dönüşleri, düşük güvenli ayırıcılar,
uzun sessizlikler ve register sıçramaları için ayrıca regresyon testlerinden
geçirilmiştir.
