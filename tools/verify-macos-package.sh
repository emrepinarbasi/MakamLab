#!/bin/zsh
set -euo pipefail

PROJECT_DIR="${0:A:h:h}"
APP_BUNDLE="${1:?Kullanım: verify-macos-package.sh APP_BUNDLE [VERSION] [release|adhoc]}"
EXPECTED_VERSION="${2:-$(tr -d '[:space:]' < "$PROJECT_DIR/MAKAM_VERSION")}"
SIGNING_MODE="${3:-adhoc}"
EXPECTED_IDENTIFIER="com.makamlab.academic"
EXPECTED_MIN_VERSION="13.5"
EXPECTED_ARCH="arm64"
PLIST="$APP_BUNDLE/Contents/Info.plist"
JPACKAGE_STATE="$APP_BUNDLE/Contents/app/.jpackage.xml"
CFG="$APP_BUNDLE/Contents/app/MakamLab.cfg"

fail() {
  print -u2 -- "macOS paket doğrulama hatası: $*"
  exit 1
}

assert_equal() {
  local label="$1" actual="$2" expected="$3"
  [[ "$actual" == "$expected" ]] || fail "$label '$expected' olmalı, bulunan: '$actual'"
}

version_le() {
  local lhs="$1" rhs="$2"
  local -a lhs_parts rhs_parts
  lhs_parts=(${(s:.:)lhs})
  rhs_parts=(${(s:.:)rhs})
  local index left right
  for index in 1 2 3; do
    left="${lhs_parts[$index]:-0}"
    right="${rhs_parts[$index]:-0}"
    (( left < right )) && return 0
    (( left > right )) && return 1
  done
  return 0
}

[[ -d "$APP_BUNDLE" ]] || fail "uygulama bulunamadı: $APP_BUNDLE"
[[ -f "$PLIST" ]] || fail "Info.plist bulunamadı"
[[ -f "$JPACKAGE_STATE" ]] || fail ".jpackage.xml bulunamadı"
[[ -f "$CFG" ]] || fail "jpackage başlatıcı yapılandırması bulunamadı"
plutil -lint "$PLIST" >/dev/null

assert_equal "CFBundleIdentifier" "$(plutil -extract CFBundleIdentifier raw -o - "$PLIST")" "$EXPECTED_IDENTIFIER"
assert_equal "CFBundleShortVersionString" "$(plutil -extract CFBundleShortVersionString raw -o - "$PLIST")" "$EXPECTED_VERSION"
assert_equal "CFBundleVersion" "$(plutil -extract CFBundleVersion raw -o - "$PLIST")" "$EXPECTED_VERSION"
assert_equal "LSMinimumSystemVersion" "$(plutil -extract LSMinimumSystemVersion raw -o - "$PLIST")" "$EXPECTED_MIN_VERSION"
assert_equal "LSArchitecturePriority[0]" "$(plutil -extract LSArchitecturePriority.0 raw -o - "$PLIST")" "$EXPECTED_ARCH"
assert_equal "LSRequiresNativeExecution" "$(plutil -extract LSRequiresNativeExecution raw -o - "$PLIST")" "true"

JPACKAGE_VERSION="$(xmllint --xpath 'string(/jpackage-state/app-version)' "$JPACKAGE_STATE")"
JPACKAGE_PLATFORM="$(xmllint --xpath 'string(/jpackage-state/@platform)' "$JPACKAGE_STATE")"
assert_equal "jpackage app-version" "$JPACKAGE_VERSION" "$EXPECTED_VERSION"
assert_equal "jpackage platform" "$JPACKAGE_PLATFORM" "macOS"
grep -Fq "java-options=-Djpackage.app-version=$EXPECTED_VERSION" "$CFG" \
  || fail "başlatıcı cfg içindeki jpackage sürümü VERSION ile eşleşmiyor"

PRIMARY_EXECUTABLE="$APP_BUNDLE/Contents/MacOS/MakamLab"
[[ -x "$PRIMARY_EXECUTABLE" ]] || fail "ana çalıştırılabilir dosya eksik"

MACHO_COUNT=0
while IFS= read -r -d '' candidate; do
  description="$(file -b "$candidate")"
  [[ "$description" == *"Mach-O"* ]] || continue
  (( MACHO_COUNT += 1 ))
  archs="$(lipo -archs "$candidate" 2>/dev/null || true)"
  assert_equal "Mach-O mimarisi ($candidate)" "$archs" "$EXPECTED_ARCH"
  minos="$(vtool -show-build "$candidate" 2>/dev/null | awk '$1 == "minos" {print $2; exit}')"
  if [[ -n "$minos" ]] && ! version_le "$minos" "$EXPECTED_MIN_VERSION"; then
    fail "$candidate en az macOS $minos istiyor; Info.plist $EXPECTED_MIN_VERSION bildiriyor"
  fi
done < <(find "$APP_BUNDLE/Contents" -type f -print0)
(( MACHO_COUNT > 0 )) || fail "Mach-O bileşen bulunamadı"

codesign --verify --deep --strict --verbose=2 "$APP_BUNDLE"
SIGNATURE_INFO="$(codesign -dvv "$APP_BUNDLE" 2>&1)"

case "$SIGNING_MODE" in
  release)
    print -r -- "$SIGNATURE_INFO" | grep -Fq 'Authority=Developer ID Application:' \
      || fail "Developer ID Application imzası yok"
    print -r -- "$SIGNATURE_INFO" | grep -Eq '^TeamIdentifier=.+$' \
      || fail "TeamIdentifier yok"
    if print -r -- "$SIGNATURE_INFO" | grep -Fq 'TeamIdentifier=not set'; then
      fail "TeamIdentifier ayarlanmamış"
    fi
    ;;
  adhoc)
    print -r -- "$SIGNATURE_INFO" | grep -Fq 'Signature=adhoc' \
      || fail "test paketinin ad-hoc imzası yok"
    ;;
  *)
    fail "bilinmeyen imza modu: $SIGNING_MODE"
    ;;
esac

print -- "macOS paket doğrulaması başarılı: sürüm=$EXPECTED_VERSION minOS=$EXPECTED_MIN_VERSION mimari=$EXPECTED_ARCH imza=$SIGNING_MODE Mach-O=$MACHO_COUNT"
