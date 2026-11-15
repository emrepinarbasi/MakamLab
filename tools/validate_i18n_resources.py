#!/usr/bin/env python3
"""Validate MakamLab's offline locale catalogue and generated bundles."""

from __future__ import annotations

import csv
import importlib.util
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GENERATOR = ROOT / "tools" / "generate_i18n_resources.py"
RESOURCE_DIR = ROOT / "src" / "main" / "resources" / "i18n"


def load_generator():
    spec = importlib.util.spec_from_file_location("makamlab_i18n_generator", GENERATOR)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line or line.lstrip().startswith(("#", "!")):
            continue
        if "=" not in line:
            raise AssertionError(f"{path.name}:{number}: missing '='")
        key, value = line.split("=", 1)
        if key in values:
            raise AssertionError(f"{path.name}:{number}: duplicate key {key}")
        values[key] = value.replace("\\n", "\n").replace("\\r", "\r").replace("\\\\", "\\")
    return values


def placeholders(value: str) -> tuple[str, ...]:
    return tuple(sorted(re.findall(r"\{\d+\}", value)))


def main() -> int:
    gen = load_generator()
    base: dict[str, str] = gen.BASE
    tags = [row[0] for row in gen.LANGUAGES]
    if tags != ["en-US", "tr-TR"]:
        raise AssertionError(f"expected the bilingual en-US/tr-TR catalogue, got {tags}")
    directions = {row[0]: row[4] for row in gen.LANGUAGES}
    rtl = {tag for tag, direction in directions.items() if direction == "rtl"}
    if rtl:
        raise AssertionError(f"the bilingual catalogue must be LTR: {sorted(rtl)}")

    with (RESOURCE_DIR / "languages.tsv").open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle, delimiter="\t"))
    if [row["tag"] for row in rows] != tags:
        raise AssertionError("languages.tsv tag order/content does not match generator")
    if any(row["translation_tier"] != "full" for row in rows):
        raise AssertionError("every locale must have translation_tier=full")
    if any(not row["flag_emoji"] or not row["native_name"] for row in rows):
        raise AssertionError("each locale requires a flag and a native display name")

    expected_keys = set(base)
    protected_names = (
        "Segâh", "Dügâh", "Bûselik", "Çargâh", "Nevâ", "Hicaz",
        "Nim Zirgüle", "Kürdilihicazkâr", "Rast", "MakamLab", "MakamBox",
        "Bilge Miraç ATICI", "Prof. Dr. Emre PINARBAŞI",
    )
    identical_allowed = {
        "about.version", "report.interpretation.groupEvidence", "source.details",
    }
    explicit_literal = {
        key for key in base
        if key.startswith("about.license.") and key not in {
            "about.license.title", "about.license.description", "about.license.viewFull"
        }
    } | {"field.makam", "report.makam"}

    errors: list[str] = []
    translated_value_count = 0
    for tag in tags:
        suffix = tag.replace("-", "_")
        path = RESOURCE_DIR / f"messages_{suffix}.properties"
        if not path.is_file():
            errors.append(f"missing bundle {path.name}")
            continue
        values = read_properties(path)
        missing = expected_keys - set(values)
        extra = set(values) - expected_keys
        if missing or extra:
            errors.append(f"{tag}: missing={len(missing)} extra={len(extra)}")
            continue
        for key, source in base.items():
            value = values[key]
            if not value.strip():
                errors.append(f"{tag}:{key}: empty value")
            if placeholders(source) != placeholders(value):
                errors.append(f"{tag}:{key}: placeholder mismatch {placeholders(source)} != {placeholders(value)}")
            for name in protected_names:
                if name in source and name not in value:
                    errors.append(f"{tag}:{key}: protected name {name!r} was changed")
            if tag not in {"en-US", "en-GB"} and value.strip() != source.strip():
                translated_value_count += 1
            if (tag not in {"en-US", "en-GB"}
                    and value.strip() == source.strip()
                    and len(source) > 15
                    and key not in identical_allowed
                    and key not in explicit_literal):
                errors.append(f"{tag}:{key}: unexpected English fallback")

    if errors:
        print("i18n validation FAILED", file=sys.stderr)
        for error in errors[:100]:
            print(" -", error, file=sys.stderr)
        if len(errors) > 100:
            print(f" - … and {len(errors) - 100} more", file=sys.stderr)
        return 1

    print(f"i18n validation OK: {len(tags)} locales × {len(base)} keys; "
          f"{translated_value_count} non-English localized values; RTL={','.join(sorted(rtl))}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
