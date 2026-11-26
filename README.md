# MakamLab 1.1.3

![MakamLab wordmark](assets/MakamLab-Wordmark-v1.png)

[Türkçe README](README_TR.md) · [Installation](INSTALLATION.md) ·
[Build from source](BUILDING.md) · [Release notes](RELEASE_NOTES_1.1.3.md)

MakamLab is an academic desktop application for analysing performed pitch,
interval, melodic direction, and glissando in Turkish makam music recordings.
It measures pitch relative to a user-confirmed tonic and reports both cents
(`C`) and Holderian commas (`Hc`, where `1 Hc = 1200/53 C`).

MakamLab is an evidence-analysis tool, not an automatic makam-diagnosis system.
Its theoretical comparisons must be interpreted together with melodic course,
cadence, dominant pitch, çeşni, modulation, recording quality, and the study
design.

## Analysis modules

1. **Pitch Analysis** — analyses one selected pitch or an open interval between
   two boundary pitches. Boundary pitches are excluded in between-pitches mode.
2. **Ascending–Descending Pitch Analysis** — separates accepted target-pitch
   events into ascending, descending, and sustained-use groups.
3. **Augmented Interval Analysis** — measures the four-degree motion and the
   augmented interval between the second and third degrees in both directions.
4. **Glissando/Portamento Analysis** — detects ascending and descending glissandi between
   two selected pitches and reports duration, span, start/end position, shape,
   and confidence.

## Method overview

- WAV, AIFF, AU, MP3, and M4A input
- user-selected tonic region or final stable-note candidate
- anti-alias-filtered 22.05 kHz analysis copy
- YIN-based fundamental-frequency tracking
- 53-Hc theoretical reference grid
- event tables with frequency, relative C/Hc, deviation, direction, duration,
  stability, and confidence
- octave-equivalent register evidence aligned to the selected theoretical
  interval while retaining physical-register audit information
- separate event tiers for strong, conditionally measurable, and audit-only
  contacts
- separate first-four-degree similarity scores for the general pitch inventory and
  waveform-confirmed descents to the tonic
- DOCX academic reports with tables, charts, method notes, and limitations
- Turkish and English interface/report output; makam and pitch proper names are
  never translated

The default first-four-pitch comparison set is **Çargâh, Kürdî, Rast, Uşşak,
and Hicaz**. Optional profiles are Sabâ, Segâh, Müstear, Hüzzam, Nikriz,
Pençgâh, Ferahnâk, and Nişabur. Bûselik is not a comparison row in version
1.1.0; Bûselik remains available as a pitch and interval boundary name.

## Supported systems

- macOS 13.5 or later, Universal (Apple Silicon and Intel)
- Windows 10/11, 64-bit (`x64`)
- Ubuntu 22.04 LTS or later, Intel/AMD 64-bit (`amd64`)
- 8 GB RAM recommended for long recordings

Java 21 is bundled with the desktop packages. Ubuntu installs FFmpeg and the
required desktop libraries through the package manager.

> **Signing notice:** version 1.1.3 community packages are not commercially
> code-signed. The macOS package is ad-hoc signed and not notarized; the Windows
> installer and portable package are unsigned. Operating-system security
> warnings may therefore appear. Verify the SHA-256 checksums before use.

## Privacy

Audio analysis and report generation run locally. MakamLab does not upload
recordings, require an account, or include telemetry. Opening a documentation
or e-mail link is the only user-initiated external action in the application.

## Build

MakamLab requires Java 21. Platform packages are generated with:

```sh
./build-makam-macos-universal.sh
./build-makam-windows-cross.sh
./build-makam-linux-deb.sh
```

See [BUILDING.md](BUILDING.md) for prerequisites and platform notes.

## Citation

Use the repository's **Cite this repository** menu, backed by
[`CITATION.cff`](CITATION.cff), or cite the software as:

> Pınarbaşı, E. (2026). *MakamLab* (Version 1.1.3) [Computer software].

## License and provenance

MakamLab is developed by **Prof. Dr. Emre PINARBAŞI** and distributed under
`GPL-3.0-or-later`. It uses theory and reference data derived from the
GPL-licensed MakamBox project by Bilge Miraç Atıcı. See
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md), [`SBOM.spdx.json`](SBOM.spdx.json),
and the [`licenses`](licenses/) directory for component-level notices.

Contact: [emrepinarbasi@gmail.com](mailto:emrepinarbasi@gmail.com)
