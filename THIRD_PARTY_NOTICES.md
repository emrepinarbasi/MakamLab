# MakamLab 1.1.0 — third-party notices

MakamLab is developed by Prof. Dr. Emre PINARBAŞI and distributed under
`GPL-3.0-or-later`. Copyright © 2026.

This inventory covers components actually used by MakamLab's pitch, interval,
direction, glissando, compressed-audio, and DOCX-report workflows. It does not
describe MakamBox Studio transcription, NeuralNote/MuScriptor, LilyPond, or
Hacklily; those components are not part of this distribution.

| Component | Version | License / distribution |
|---|---:|---|
| MakamLab | 1.1.0 | GPL-3.0-or-later |
| MakamBox theory/reference data | recorded revision `fdfbd03531af…` | GPL-3.0-or-later |
| JLayer | 1.0.1 | LGPL-2.1-only |
| Apache POI / POI OOXML | 6.0.0 | Apache-2.0 |
| Apache XMLBeans | 5.4.1 | Apache-2.0 |
| Apache Log4j API/Core | 2.26.1 | Apache-2.0 |
| Apache Commons Codec | 1.23.0 | Apache-2.0 |
| Apache Commons Collections | 4.6.0 | Apache-2.0 |
| Apache Commons Compress | 1.28.0 | Apache-2.0 |
| Apache Commons IO | 2.22.0 | Apache-2.0 |
| Apache Commons Math | 3.6.1 | Apache-2.0 |
| SparseBitSet | 1.3 | Apache-2.0 |
| curvesapi | 1.08 | BSD-3-Clause; bundled Harmony Crossing code: Apache-2.0 |
| Java runtime | 21 | GPL-2.0 with Classpath Exception; distribution varies by platform |
| FFmpeg, Windows package | 9.0.2 LGPL shared build | LGPL-2.1-or-later; separate process |
| FFmpeg, Ubuntu package | Ubuntu repository version | system package; separate process |

## MakamBox data

Makam intervals, tonic mappings, templates, and related reference resources are
adapted from Bilge Miraç Atıcı's GPL-licensed MakamBox project:
<https://github.com/miracatici/MakamBox>. The recorded source revision and
conversion note are in `src/main/resources/legacy/ORIGIN.md`.

## Audio support

JLayer is used for MP3 decoding. On Windows, the packaged LGPL shared FFmpeg
build is invoked as a separate process for M4A/AAC and formats not opened by
Java Sound. Ubuntu uses the distribution's `ffmpeg` package, declared as a DEB
dependency. The macOS application uses the available platform/audio paths
documented by the build.

The Windows FFmpeg origin, archive identifier, and checksum are recorded in
`licenses/FFmpeg-LGPL-Notice.txt` and
`MAKAMLAB_WINDOWS_THIRD_PARTY_NOTICES.md`.

## DOCX reporting

Apache POI and POI OOXML generate Word documents. XMLBeans, Log4j, Apache
Commons components, SparseBitSet, and curvesapi are their packaged runtime
dependencies. Upstream notices embedded in each JAR remain intact.

## Platform notices

- `MAKAMLAB_THIRD_PARTY_NOTICES.md`: general/macOS package notice
- `MAKAMLAB_WINDOWS_THIRD_PARTY_NOTICES.md`: Windows package notice
- `MAKAMLAB_LINUX_THIRD_PARTY_NOTICES.md`: Ubuntu package notice
- `licenses/`: license texts and origin-specific notices

The machine-readable inventory is `SBOM.spdx.json`.
