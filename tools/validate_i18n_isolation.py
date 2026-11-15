#!/usr/bin/env python3
"""Release gate for MakamLab locale isolation.

This validator deliberately works on the generated ``.properties`` files *and*
the long-form translation catalogue.  A complete generated bundle is not
enough on its own: Java ``ResourceBundle`` would silently fall back to the base
English bundle when a catalogue entry is missing.  Requiring every locale to
have every key prevents that kind of mixed-language window.

Makam, pitch, person, product and licence identifiers are proper names.  When
one of those literals occurs in the English source text it must occur byte for
byte in every translation; surrounding prose may be translated, the name may
not be transliterated.
"""

from __future__ import annotations

import csv
import importlib.util
import re
import sys
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RESOURCE_DIR = ROOT / "src" / "main" / "resources" / "i18n"
CATALOGUE = ROOT / "tools" / "i18n-translations.tsv"
GENERATOR = ROOT / "tools" / "generate_i18n_resources.py"


# Scientific display names that can also occur in explanatory prose.  The UI
# obtains the full Makam/perde lists dynamically from TheoryCatalog; this list
# guards the subset which can occur in static report/help sentences.
PROTECTED_NAMES = (
    "MakamLab",
    "MakamBox",
    "Bilge Miraç ATICI",
    "Prof. Dr. Emre PINARBAŞI",
    "Acem",
    "Acemaşiran",
    "Bûselik",
    "Çargâh",
    "Dügâh",
    "Eviç",
    "Gerdaniye",
    "Geveşt",
    "Hicaz",
    "Hisar",
    "Hüseynî",
    "Irak",
    "Kürdî",
    "Kürdilihicazkâr",
    "Mâhur",
    "Muhayyer",
    "Nevâ",
    "Nim Zirgüle",
    "Rast",
    "Segâh",
    "Şehnâz",
    "Sünbüle",
    "Uşşak",
    "Hüzzam",
    "Yegâh",
    "Zirgüle",
)

MESSAGE_ARGUMENT = re.compile(r"(?<!\\)\{(\d+)(?:,[^{}]*)?\}")
LATIN_WORD = re.compile(r"[A-Za-zÀ-ž]+")
TURKISH_MARKER = re.compile(
    r"[ğĞıİşŞçÇöÖüÜ]|\b(?:ve|ile|için|olarak|seçili|analiz|rapor|perde|karar)\b",
    re.IGNORECASE,
)

# For locales whose normal writing system is unambiguous, a substantial
# translated sentence must contain that writing system.  This catches a row
# which is technically present in the catalogue but was accidentally pasted
# from another language.  Proper names and short scientific labels are not
# subjected to this test.
TARGET_SCRIPT_MARKERS = {
    "zh-Hans-CN": re.compile(r"[\u3400-\u9fff]"),
    "zh-Hant-TW": re.compile(r"[\u3400-\u9fff]"),
    # Japanese prose may legitimately consist only of kanji (for example a
    # compact chart title), so accept kana *or* CJK ideographs.
    "ja-JP": re.compile(r"[\u3040-\u30ff\u3400-\u9fff]"),
    "ko-KR": re.compile(r"[\uac00-\ud7af]"),
    "ar-SA": re.compile(r"[\u0600-\u06ff]"),
    "fa-IR": re.compile(r"[\u0600-\u06ff]"),
    "ur-PK": re.compile(r"[\u0600-\u06ff]"),
    "he-IL": re.compile(r"[\u0590-\u05ff]"),
    "el-GR": re.compile(r"[\u0370-\u03ff]"),
    "ru-RU": re.compile(r"[\u0400-\u04ff]"),
    "uk-UA": re.compile(r"[\u0400-\u04ff]"),
    "bg-BG": re.compile(r"[\u0400-\u04ff]"),
    "sr-Cyrl-RS": re.compile(r"[\u0400-\u04ff]"),
    "kk-KZ": re.compile(r"[\u0400-\u04ff]"),
    "hi-IN": re.compile(r"[\u0900-\u097f]"),
    "mr-IN": re.compile(r"[\u0900-\u097f]"),
    "ne-NP": re.compile(r"[\u0900-\u097f]"),
    "bn-BD": re.compile(r"[\u0980-\u09ff]"),
    "pa-IN": re.compile(r"[\u0a00-\u0a7f]"),
    "gu-IN": re.compile(r"[\u0a80-\u0aff]"),
    "ta-IN": re.compile(r"[\u0b80-\u0bff]"),
    "te-IN": re.compile(r"[\u0c00-\u0c7f]"),
    "kn-IN": re.compile(r"[\u0c80-\u0cff]"),
    "ml-IN": re.compile(r"[\u0d00-\u0d7f]"),
    "si-LK": re.compile(r"[\u0d80-\u0dff]"),
    "my-MM": re.compile(r"[\u1000-\u109f]"),
    "ka-GE": re.compile(r"[\u10a0-\u10ff]"),
    "hy-AM": re.compile(r"[\u0530-\u058f]"),
    "km-KH": re.compile(r"[\u1780-\u17ff]"),
    "am-ET": re.compile(r"[\u1200-\u137f]"),
}
ENGLISH_PROSE_MARKER = re.compile(
    r"\b(?:the|a|an|and|or|to|for|of|in|is|are|with|from|this|that|when|while|"
    r"analysis|report|selected|audio|pitch|value|values|mean|median)\b",
    re.IGNORECASE,
)


def load_generator():
    spec = importlib.util.spec_from_file_location("makamlab_i18n_generator", GENERATOR)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Cannot import {GENERATOR}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def unescape_property(value: str) -> str:
    result: list[str] = []
    index = 0
    while index < len(value):
        character = value[index]
        if character == "\\" and index + 1 < len(value):
            index += 1
            escaped = value[index]
            result.append({"n": "\n", "r": "\r", "t": "\t"}.get(escaped, escaped))
        else:
            result.append(character)
        index += 1
    return "".join(result)


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line or line.lstrip().startswith(("#", "!")):
            continue
        if "=" not in line:
            raise ValueError(f"{path}:{line_number}: expected key=value")
        key, value = line.split("=", 1)
        if key in values:
            raise ValueError(f"{path}:{line_number}: duplicate key {key!r}")
        values[key] = unescape_property(value)
    return values


def placeholders(value: str) -> tuple[str, ...]:
    return tuple(sorted(MESSAGE_ARGUMENT.findall(value)))


def long_natural_language(value: str) -> bool:
    """Conservative test used only to catch an entire untranslated sentence."""
    return len(value) >= 24 and len(LATIN_WORD.findall(value)) >= 4


def main() -> int:
    generator = load_generator()
    expected_tags = [row[0] for row in generator.LANGUAGES]
    expected_keys = set(generator.BASE)
    errors: list[str] = []
    placeholder_mismatches = 0
    protected_name_mismatches = 0
    english_sentence_leaks = 0
    turkish_sentence_leaks = 0
    target_script_mismatches = 0

    if expected_tags != ["en-US", "tr-TR"]:
        errors.append("language list must contain only en-US and tr-TR")

    base_path = RESOURCE_DIR / "messages.properties"
    base = read_properties(base_path)
    if base != generator.BASE:
        missing = sorted(expected_keys - set(base))
        extra = sorted(set(base) - expected_keys)
        changed = sorted(key for key in expected_keys & set(base) if base[key] != generator.BASE[key])
        errors.append(
            "base bundle is stale relative to generator: "
            f"missing={missing[:5]} extra={extra[:5]} changed={changed[:5]}"
        )

    # Catalogue coverage is checked separately from generated properties so a
    # base-English fallback can never masquerade as a complete translation.
    catalogue: dict[str, dict[str, str]] = defaultdict(dict)
    duplicate_rows: list[str] = []
    with CATALOGUE.open(encoding="utf-8", newline="") as handle:
        for row_number, row in enumerate(csv.DictReader(handle, delimiter="\t"), 2):
            locale = (row.get("locale") or "").strip()
            if locale not in expected_tags:
                continue
            key = (row.get("key") or "").strip()
            value = row.get("value") or ""
            if key in catalogue[locale]:
                duplicate_rows.append(f"{locale}/{key} (row {row_number})")
            catalogue[locale][key] = value
    if duplicate_rows:
        errors.append("duplicate translation rows: " + ", ".join(duplicate_rows[:10]))
    if set(catalogue) != set(expected_tags):
        errors.append(
            "translation catalogue locale mismatch: "
            f"missing={sorted(set(expected_tags) - set(catalogue))} "
            f"extra={sorted(set(catalogue) - set(expected_tags))}"
        )

    language_table: dict[str, dict[str, str]] = {}
    with (RESOURCE_DIR / "languages.tsv").open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle, delimiter="\t"):
            language_table[row["tag"]] = row
    if set(language_table) != set(expected_tags):
        errors.append("languages.tsv tags do not match the supported locale list")

    bundles: dict[str, dict[str, str]] = {}
    for row in generator.LANGUAGES:
        tag, _language, _script, _region, direction = row[:5]
        expected_file = RESOURCE_DIR / f"messages_{tag.replace('-', '_')}.properties"
        if not expected_file.is_file():
            errors.append(f"{tag}: bundle is missing: {expected_file.name}")
            continue
        translated = catalogue.get(tag, {})
        missing_catalogue = expected_keys - set(translated)
        extra_catalogue = set(translated) - expected_keys
        if missing_catalogue or extra_catalogue:
            errors.append(
                f"{tag}: catalogue must explicitly cover every key; "
                f"missing={sorted(missing_catalogue)[:8]} extra={sorted(extra_catalogue)[:8]}"
            )
        empty = sorted(key for key in expected_keys & set(translated) if not translated[key].strip())
        if empty:
            errors.append(f"{tag}: empty catalogue values: {empty[:8]}")

        bundle = read_properties(expected_file)
        bundles[tag] = bundle
        missing_bundle = expected_keys - set(bundle)
        extra_bundle = set(bundle) - expected_keys
        if missing_bundle or extra_bundle:
            errors.append(
                f"{tag}: bundle key mismatch; "
                f"missing={sorted(missing_bundle)[:8]} extra={sorted(extra_bundle)[:8]}"
            )
            continue
        if language_table.get(tag, {}).get("translation_tier") != "full":
            errors.append(f"{tag}: translation tier is not full")
        if language_table.get(tag, {}).get("direction") != direction:
            errors.append(f"{tag}: direction differs between generator and languages.tsv")

        for key in sorted(expected_keys):
            value = bundle[key]
            if value != translated.get(key):
                errors.append(f"{tag}/{key}: generated bundle differs from translation catalogue")
            if placeholders(base[key]) != placeholders(value):
                placeholder_mismatches += 1
                errors.append(
                    f"{tag}/{key}: MessageFormat arguments differ: "
                    f"{placeholders(base[key])} != {placeholders(value)}"
                )
            for name in PROTECTED_NAMES:
                if name in base[key] and name not in value:
                    protected_name_mismatches += 1
                    errors.append(f"{tag}/{key}: protected name was changed or transliterated: {name}")

            # A whole English sentence copied into another-language bundle is
            # a reliable sign of fallback leakage. Short technical labels are
            # excluded deliberately (Hz, F0, MIDI, licence identifiers, etc.).
            if (tag not in {"en-US", "en-GB"}
                    and not key.startswith("about.license.")
                    and value == base[key]
                    and long_natural_language(value)):
                english_sentence_leaks += 1
                errors.append(f"{tag}/{key}: untranslated English sentence: {value!r}")

            script_marker = TARGET_SCRIPT_MARKERS.get(tag)
            if (script_marker is not None
                    and not key.startswith("about.license.")
                    and long_natural_language(base[key])
                    and ENGLISH_PROSE_MARKER.search(base[key])
                    and not script_marker.search(value)):
                target_script_mismatches += 1
                errors.append(
                    f"{tag}/{key}: substantial prose does not use the target writing system: {value!r}"
                )

    # Detect a full Turkish sentence copied into an unrelated locale. Azerbaijani
    # and Uzbek legitimately share many short/medium expressions with Turkish;
    # their translations are not treated as leakage solely on equality.
    turkish = bundles.get("tr-TR", {})
    for tag, bundle in bundles.items():
        if tag in {"tr-TR", "az-Latn-AZ", "uz-Latn-UZ"}:
            continue
        for key, value in bundle.items():
            if (value != base.get(key)
                    and value == turkish.get(key)
                    and len(value) >= 28
                    and len(LATIN_WORD.findall(value)) >= 5
                    and TURKISH_MARKER.search(value)):
                turkish_sentence_leaks += 1
                errors.append(f"{tag}/{key}: Turkish sentence leaked into locale: {value!r}")

    if errors:
        print(f"I18N_ISOLATION_FAILED errors={len(errors)}", file=sys.stderr)
        for error in errors:
            print(" - " + error, file=sys.stderr)
        return 1

    print(
        "I18N_ISOLATION_OK "
        f"locales={len(expected_tags)} keys={len(expected_keys)} "
        f"catalogue_rows={sum(len(values) for values in catalogue.values())} "
        f"placeholder_mismatches={placeholder_mismatches} "
        f"protected_names={len(PROTECTED_NAMES)} "
        f"protected_name_mismatches={protected_name_mismatches} "
        f"english_sentence_leaks={english_sentence_leaks} "
        f"turkish_sentence_leaks={turkish_sentence_leaks}"
        f" target_script_mismatches={target_script_mismatches}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
