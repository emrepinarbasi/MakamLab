# Changelog

All notable changes are documented here. MakamLab follows semantic versioning
for public releases.

## [1.1.0] — 2026-11-15

### Added

- Four task-focused modules: Pitch Analysis, Ascending–Descending Pitch
  Analysis, Augmented Interval Analysis, and Glissando Analysis.
- Single-pitch and open between-pitches analysis with explicit boundary
  exclusion.
- Manual tonic-region selection from the waveform and final stable-note tonic
  candidate.
- DOCX reports with academic tables, charts, method notes, definitions, and
  limitations.
- Native packages for Apple Silicon macOS, Windows x64, and Ubuntu amd64.
- Turkish and English interface and report language.

### Analysis and reporting

- YIN-based F0 analysis on an anti-alias-filtered 22.05 kHz working copy.
- C and 53-Hc measurements with signed theoretical deviation.
- Strong, conditionally measurable, and audit-only event tiers.
- Reliable octave/register evidence aligned to the selected theoretical pitch
  class without treating isolated octave errors as new evidence.
- First-four-pitch similarity based on one shared tonic-aligned observation set.
- Core comparison profiles: Çargâh, Kürdî, Rast, Uşşak, and Hicaz.
- Optional comparison profiles: Sabâ, Segâh, Müstear, Hüzzam, Nikriz, Pençgâh,
  Ferahnâk, and Nişabur.
- Bûselik removed from comparison ranking while remaining available as a pitch
  and interval-boundary name.

### Reliability

- Regression coverage for tonic selection, F0 tracking, MP3/M4A loading,
  register handling, event tiers, profile scoring, reports, playback, and UI
  layout.
- Linux release gate with real compressed-audio decoding, package installation,
  and GUI launch in a clean Ubuntu-compatible container.

### Known distribution limitations

- macOS 1.1.0 community build is ad-hoc signed and not notarized.
- Windows 1.1.0 community builds are unsigned and may trigger SmartScreen.
