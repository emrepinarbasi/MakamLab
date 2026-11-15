#!/usr/bin/env python3
"""Fail a release when MakamLab references an unregistered i18n key.

The ordinary bundle validator proves that every supported locale covers BASE.
This companion gate proves that the report producers do not ask for a key that
BASE itself does not know.  Exact-locale lookup deliberately renders a missing
key as ``[key]``; catching it here keeps that diagnostic out of a released DOCX.

Only literal key-like strings are accepted in report presentation code.  The
one dynamic lookup below is the central ReportLocalization bridge; it is
allowlisted explicitly rather than permitting arbitrary dynamic prefixes.
"""

from __future__ import annotations

import importlib.util
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GENERATOR = ROOT / "tools" / "generate_i18n_resources.py"
MAKAMLAB_ROOT = ROOT / "src" / "main" / "java" / "com" / "makambox" / "studio"
REPORT_ROOT = MAKAMLAB_ROOT / "report" / "docx"
HANDLER = ROOT / "src" / "main" / "java" / "com" / "makambox" / "studio" / "ui" / "MakamDocxReportHandler.java"

KEY_LITERAL = re.compile(
    r'"((?:about|action|analysis|audio|canvas|common|dialog|field|format|header|language|menu|progress|report|result|source|status|theory|tonic|unit|validation|waveEditor)\.[A-Za-z0-9_.-]+)"'
)
DYNAMIC_RESOURCE_LOOKUP = re.compile(r"textOrDefault\(\s*([A-Za-z_$][A-Za-z0-9_$]*)\s*,")

# (file name, variable used as the dynamic key).  Prefix-based or computed
# report keys must be added here deliberately and covered by a separate test.
DYNAMIC_KEY_ALLOWLIST = {
    ("ReportLocalization.java", "key"),
}


def load_base() -> dict[str, str]:
    spec = importlib.util.spec_from_file_location("makamlab_i18n_generator", GENERATOR)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Cannot import {GENERATOR}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.BASE


def main() -> int:
    report_sources = set(sorted(REPORT_ROOT.glob("*.java")) + [HANDLER])
    sources = []
    for source in sorted(MAKAMLAB_ROOT.rglob("*.java")):
        if source.name.startswith("._") or "transcription" in source.parts:
            continue
        if source.name in {"StudioFrame.java", "StudioMenuBar.java",
                           "MakamsalTranscriptionPanel.java", "MakamBoxStudio.java"}:
            continue
        sources.append(source)
    used: set[str] = set()
    unexpected_dynamic: list[str] = []
    for source in sources:
        text = source.read_text(encoding="utf-8")
        used.update(KEY_LITERAL.findall(text))
        if source in report_sources:
            for variable in DYNAMIC_RESOURCE_LOOKUP.findall(text):
                if (source.name, variable) not in DYNAMIC_KEY_ALLOWLIST:
                    unexpected_dynamic.append(f"{source.name}: textOrDefault({variable}, ...)")

    base = load_base()
    missing = sorted(used - set(base))
    if missing or unexpected_dynamic:
        print("REPORT_I18N_KEY_GATE_FAILED", file=sys.stderr)
        if missing:
            print(" - keys used by report code but absent from BASE:", file=sys.stderr)
            for key in missing:
                print(f"   {key}", file=sys.stderr)
        if unexpected_dynamic:
            print(" - non-allowlisted dynamic resource lookups:", file=sys.stderr)
            for value in unexpected_dynamic:
                print(f"   {value}", file=sys.stderr)
        return 1

    print(
        "MAKAMLAB_I18N_KEY_GATE_OK "
        f"sources={len(sources)} literal_keys={len(used)} "
        f"dynamic_allowlist={len(DYNAMIC_KEY_ALLOWLIST)} base_keys={len(base)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
