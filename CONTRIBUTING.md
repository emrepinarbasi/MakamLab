# Contributing to MakamLab

Contributions that improve reproducibility, accessibility, audio-format support,
report clarity, tests, or documented analysis behaviour are welcome.

## Before opening a change

1. Search existing issues.
2. For methodological changes, describe the musical and statistical assumption
   being changed and provide a reproducible audio or synthetic test case that
   you have the right to share.
3. Do not change makam or pitch proper names through localisation.
4. Do not silently alter thresholds, evidence tiers, 53-Hc references, or score
   formulas. Such changes require tests and release-note documentation.

## Development workflow

1. Create a focused branch.
2. Build with Java 21.
3. Run the relevant regression programs and both localisation validators.
4. Keep audio recordings, generated reports, package outputs, signing keys, and
   local configuration outside the commit.
5. Update the changelog when user-visible behaviour changes.

## Pull requests

A pull request should explain:

- the problem and expected behaviour;
- analysis/reporting consequences;
- tests added or run;
- platforms checked;
- any licensing or third-party dependency effect.

By submitting a contribution, you agree that it is distributed under the
project's `GPL-3.0-or-later` license.
