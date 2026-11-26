#!/bin/zsh
set -euo pipefail

project="${0:A:h}"
x64_jdk="${MAKAM_X64_JDK:?Set MAKAM_X64_JDK to the Intel Java 21 JDK home}"
arm_jdk="${MAKAM_ARM64_JDK:?Set MAKAM_ARM64_JDK to the Apple Silicon Java 21 JDK home}"
version="$(tr -d '[:space:]' < "$project/MAKAM_VERSION")"
build="$project/build/makam"
dist="$build/dist"
dmg="$dist/MakamLab-V${version}-macOS-Universal.dmg"
tmp="$(mktemp -d "$build/.universal.XXXXXX" 2>/dev/null || true)"
if [[ -z "$tmp" ]]; then
  mkdir -p "$build"
  tmp="$(mktemp -d "$build/.universal.XXXXXX")"
fi
trap 'rm -rf "$tmp"' EXIT

require_architecture() {
  local binary="$1" architecture="$2" actual
  actual="$(lipo -archs "$binary")"
  if [[ " $actual " != *" $architecture "* ]]; then
    print -u2 -- "$binary: $architecture bekleniyordu, bulunan: $actual"
    return 1
  fi
}

require_architecture "$x64_jdk/bin/java" x86_64
require_architecture "$arm_jdk/bin/java" arm64
if [[ "$(uname -m)" == arm64 ]]; then native_jdk="$arm_jdk"; else native_jdk="$x64_jdk"; fi

PATH="$native_jdk/bin:$PATH" UNIVERSAL_STAGE=1 APP_ONLY=1 zsh "$project/build-makam-macos.sh"
mkdir -p "$tmp/image"
ditto "$dist/MakamLab.app" "$tmp/image/MakamLab.app"
app="$tmp/image/MakamLab.app"
contents="$app/Contents"
rm -rf "$contents/runtime"

runtime_modules="java.se,jdk.unsupported,jdk.unsupported.desktop,jdk.crypto.ec,jdk.charsets,jdk.localedata,jdk.zipfs"
for arch in x86_64 arm64; do
  if [[ "$arch" == arm64 ]]; then target_jdk="$arm_jdk"; else target_jdk="$x64_jdk"; fi
  "$native_jdk/bin/jlink" --module-path "$target_jdk/jmods" --add-modules "$runtime_modules" \
    --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
    --output "$contents/runtime-$arch"
  require_architecture "$contents/runtime-$arch/bin/java" "$arch"
  require_architecture "$contents/runtime-$arch/lib/server/libjvm.dylib" "$arch"
done
xcrun clang -Wall -Wextra -Werror -arch x86_64 -arch arm64 -mmacosx-version-min=13.5 \
  "$project/macos/universal-launcher.c" -o "$contents/MacOS/MakamLab"
require_architecture "$contents/MacOS/MakamLab" x86_64
require_architecture "$contents/MacOS/MakamLab" arm64
plutil -remove LSArchitecturePriority "$contents/Info.plist"
codesign --force --deep --sign - "$app"
codesign --verify --deep --strict --verbose=2 "$app"
ln -s /Applications "$tmp/image/Applications"
hdiutil create -quiet -volname "MakamLab $version Universal" -srcfolder "$tmp/image" \
  -format UDZO "$tmp/MakamLab.dmg"
codesign --force --sign - "$tmp/MakamLab.dmg"
codesign --verify --verbose=2 "$tmp/MakamLab.dmg"
hdiutil verify "$tmp/MakamLab.dmg"
mv -f "$tmp/MakamLab.dmg" "$dmg"
print -- "$dmg"
