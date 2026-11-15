# MakamLab V1.1 — Windows lisans ve üçüncü taraf bildirimleri

MakamLab, Prof. Dr. Emre PINARBAŞI tarafından geliştirilmiştir.
İletişim: `emrepinarbasi@gmail.com`. Copyright © 2026.

MakamLab, **GPL-3.0-or-later** koşulları altında dağıtılır. Tam lisans
metni paket içindeki `LICENSE.txt` dosyasındadır.

Bu bildirim, Windows MakamLab paketinde fiilen bulunan perde, aralık, yön,
glissando analizi, ses dosyası okuma ve DOCX raporlama bileşenlerini kapsar.

| Bileşen | Lisans |
|---|---|
| MakamLab V1.1 | GPL-3.0-or-later |
| MakamBox v1.0 kuram ve referans verileri | GPL-3.0-or-later |
| JLayer 1.0.1 | LGPL-2.1 |
| FFmpeg 9.0.2 Windows ses dönüştürme bileşeni | LGPL-2.1-or-later |
| Apache POI / POI OOXML 6.0.0 | Apache-2.0 |
| Apache XMLBeans 5.4.1 | Apache-2.0 |
| Apache Log4j API / Core 2.26.1 | Apache-2.0 |
| Apache Commons Codec / Collections / Compress / IO / Math | Apache-2.0 |
| SparseBitSet 1.3 | Apache-2.0 |
| curvesapi 1.08 | BSD-3-Clause |
| curvesapi içindeki Apache Harmony `Crossing` sınıfı | Apache-2.0 |
| Eclipse Temurin OpenJDK Runtime 21 | GPL-2.0 with Classpath Exception |

## MakamBox v1.0

Kuram oranları, makam/karar perdesi eşleştirmeleri ve ilişkili referans
verileri Bilge Miraç Atıcı'nın MakamBox projesinden uyarlanmıştır. Özgün
MakamBox çalışması GPL-3.0-or-later altında dağıtılır.

Kaynak: https://github.com/miracatici/MakamBox

## JLayer 1.0.1

MP3 dosyalarını PCM/WAV verisine dönüştürmek için kullanılır ve
LGPL-2.1 altında dağıtılır.

## FFmpeg 9.0.2

Windows paketinde M4A/AAC ve Java Sound tarafından doğrudan açılamayan ses
dosyalarını PCM/WAV çalışma kopyasına dönüştürmek için değiştirilmemiş FFmpeg
LGPL shared build kullanılır. FFmpeg, LGPL-2.1-or-later koşulları altındadır;
ilgili bildirim ve yeniden bağlama için gereken paylaşımlı DLL dosyaları
pakette birlikte sunulur.

Kaynak: https://ffmpeg.org/

## Apache POI ve Word raporu bağımlılıkları

DOCX raporları için Apache POI/POI OOXML; bunlarla birlikte XMLBeans, Log4j,
Apache Commons bileşenleri ve SparseBitSet kullanılır. Bu bileşenler
Apache-2.0 altındadır. curvesapi BSD-3-Clause; içindeki Apache Harmony
`Crossing` sınıfı ise Apache-2.0 kapsamındadır.

## Java çalışma zamanı

Windows uygulamasıyla birlikte verilen Eclipse Temurin OpenJDK Runtime 21,
GPL-2.0 with Classpath Exception ve kendi paket içi yasal bildirimleri
kapsamındadır.
