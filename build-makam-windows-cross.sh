#!/bin/zsh
set -euo pipefail

PROJECT_DIR="${0:A:h}"
APP_VERSION="$(tr -d '[:space:]' < "$PROJECT_DIR/MAKAM_VERSION")"
DEPS_DIR="${MAKAM_WINDOWS_DEPS_DIR:-$PROJECT_DIR/build/dependencies/windows}"
BUILD_DIR="$PROJECT_DIR/build/makam/windows-x64"
CLASS_DIR="$BUILD_DIR/classes"
TEST_DIR="$BUILD_DIR/test-classes"
PACKAGE_CLASS_DIR="$BUILD_DIR/package-classes"
STAGE_DIR="$BUILD_DIR/portable"
APP_DIR="$STAGE_DIR/app"
RESOURCE_DIR="$STAGE_DIR/resources"
DIST_DIR="$PROJECT_DIR/build/makam/windows-dist"
RC_DIR="$BUILD_DIR/rc"

JRE_FILE="OpenJDK21U-jre_x64_windows_hotspot_21.0.13_9.zip"
JRE_ARCHIVE="$DEPS_DIR/$JRE_FILE"
JRE_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/$JRE_FILE"
JRE_CHECKSUM="2332c0b4cff6bed722da3567c91d5e47568bcba863fa4945d0a8d6fc76158628"

FFMPEG_VERSION="n9.0.2-57-gd838f9f9ae"
FFMPEG_FILE="ffmpeg-$FFMPEG_VERSION-win64-lgpl-shared-9.0.zip"
FFMPEG_ARCHIVE="$DEPS_DIR/$FFMPEG_FILE"
FFMPEG_URL="https://github.com/FFmpeg-Builds/FFmpeg-Builds/releases/download/autobuild-2026-11-13-12-43/$FFMPEG_FILE"
FFMPEG_CHECKSUM="1604210cff6b8814ad3e925719234acdd465226c20f49b7f7a54847292e88181"

ZIG_VERSION="0.17.0"
case "$(uname -m)" in
  arm64|aarch64)
    ZIG_PLATFORM="aarch64-macos"
    ZIG_CHECKSUM="b607e9b9234790a008116ae5bdb71c6243b84b9fb42a53a9e70fde41c06c536a"
    ;;
  x86_64|amd64)
    ZIG_PLATFORM="x86_64-macos"
    ZIG_CHECKSUM="4f9a1c5269aa17ebda5e6d3c2b89d6cbf36f7d2b22a0306e9ab98f25f95529c6"
    ;;
  *) print -u2 -- "Desteklenmeyen derleme mimarisi: $(uname -m)"; exit 1 ;;
esac
ZIG_ARCHIVE="$DEPS_DIR/zig-$ZIG_PLATFORM-$ZIG_VERSION.tar.xz"
ZIG_DIR="$DEPS_DIR/zig-$ZIG_VERSION"
ZIG="$ZIG_DIR/zig"

if [[ -n "${WINDOWS_SIGN_PFX:-}" ]]; then
  SIGNING_SUFFIX=""
else
  SIGNING_SUFFIX="-UNSIGNED-TEST"
fi
OUTPUT_SETUP="$DIST_DIR/MakamLab-$APP_VERSION-Windows-x64-Setup$SIGNING_SUFFIX.exe"
OUTPUT_PORTABLE="$DIST_DIR/MakamLab-$APP_VERSION-Windows-x64-Portable$SIGNING_SUFFIX.zip"
OUTPUT_SUMS="$DIST_DIR/MakamLab-$APP_VERSION-Windows-x64-SHA256SUMS.txt"

# Keep Windows reports under the same exact-locale release gates as macOS.
python3 "$PROJECT_DIR/tools/validate_report_i18n_keys.py"
python3 "$PROJECT_DIR/tools/validate_i18n_isolation.py"

download_and_verify() {
  local url="$1" destination="$2" checksum="$3"
  if [[ ! -f "$destination" ]] || [[ "$(shasum -a 256 "$destination" | awk '{print $1}')" != "$checksum" ]]; then
    curl -fL --retry 5 --retry-all-errors "$url" -o "$destination"
  fi
  echo "$checksum  $destination" | shasum -a 256 -c -
}

sign_executable() {
  local executable="$1"
  [[ -n "${WINDOWS_SIGN_PFX:-}" ]] || return 0
  [[ -f "$WINDOWS_SIGN_PFX" ]] || { print -u2 -- "PFX bulunamadı: $WINDOWS_SIGN_PFX"; exit 1; }
  command -v osslsigncode >/dev/null || {
    print -u2 -- "WINDOWS_SIGN_PFX ayarlı ancak osslsigncode bulunamadı."; exit 1
  }
  local signed="$executable.signed"
  rm -f "$signed"
  osslsigncode sign -pkcs12 "$WINDOWS_SIGN_PFX" \
    -pass "${WINDOWS_SIGN_PASSWORD:-}" \
    -n "MakamLab" -i "https://github.com/" \
    -ts "${WINDOWS_TIMESTAMP_URL:-http://timestamp.sectigo.com}" \
    -in "$executable" -out "$signed"
  mv "$signed" "$executable"
  osslsigncode verify -in "$executable"
}

mkdir -p "$DEPS_DIR" "$BUILD_DIR" "$CLASS_DIR" "$TEST_DIR" "$PACKAGE_CLASS_DIR" "$STAGE_DIR" \
  "$APP_DIR" "$RESOURCE_DIR" "$DIST_DIR" "$RC_DIR"
find "$CLASS_DIR" -mindepth 1 -delete
find "$TEST_DIR" -mindepth 1 -delete
find "$PACKAGE_CLASS_DIR" -mindepth 1 -delete
find "$STAGE_DIR" -mindepth 1 -delete
find "$RC_DIR" -mindepth 1 -delete
find "$DIST_DIR" -mindepth 1 -delete
mkdir -p "$APP_DIR" "$RESOURCE_DIR" "$STAGE_DIR/licenses" "$RC_DIR"

download_and_verify \
  "https://ziglang.org/download/$ZIG_VERSION/zig-$ZIG_PLATFORM-$ZIG_VERSION.tar.xz" \
  "$ZIG_ARCHIVE" "$ZIG_CHECKSUM"
if [[ ! -x "$ZIG" ]]; then
  mkdir -p "$ZIG_DIR"
  find "$ZIG_DIR" -mindepth 1 -delete
  tar -xJf "$ZIG_ARCHIVE" -C "$ZIG_DIR" --strip-components=1
fi
download_and_verify "$JRE_URL" "$JRE_ARCHIVE" "$JRE_CHECKSUM"
download_and_verify "$FFMPEG_URL" "$FFMPEG_ARCHIVE" "$FFMPEG_CHECKSUM"

typeset -a LIB_JARS MAIN_SOURCES TEST_SOURCES
while IFS= read -r -d $'\0' jar_file; do LIB_JARS+=("$jar_file"); done \
  < <(find "$PROJECT_DIR/lib" -maxdepth 1 -name '*.jar' ! -name '._*' -print0)
LIB_CP="${(j.:.)LIB_JARS}"
while IFS= read -r -d $'\0' source_file; do MAIN_SOURCES+=("$source_file"); done \
  < <(find "$PROJECT_DIR/src/main/java" -name '*.java' ! -name '._*' -print0)
while IFS= read -r -d $'\0' source_file; do TEST_SOURCES+=("$source_file"); done \
  < <(find "$PROJECT_DIR/src/test/java" -name '*.java' ! -name '._*' -print0)

javac --release 21 -encoding UTF-8 -cp "$LIB_CP" -d "$CLASS_DIR" "${MAIN_SOURCES[@]}"
cp -R "$PROJECT_DIR/src/main/resources/." "$CLASS_DIR/"
mkdir -p "$CLASS_DIR/ui"
cp "$PROJECT_DIR/assets/MakamIcon-1024.png" "$CLASS_DIR/ui/MakamIcon-1024.png"
cp "$PROJECT_DIR/assets/MakamLab-Wordmark-v1.png" "$CLASS_DIR/ui/MakamLab-Wordmark-v1.png"
cp "$PROJECT_DIR/assets/MenuIcon-PerdeAnalizi-v1.png" "$CLASS_DIR/ui/MenuIcon-PerdeAnalizi-v1.png"
cp "$PROJECT_DIR/assets/MenuIcon-IniciCikici-v1.png" "$CLASS_DIR/ui/MenuIcon-IniciCikici-v1.png"
cp "$PROJECT_DIR/assets/MenuIcon-ArtikAralik-v1-preview.png" "$CLASS_DIR/ui/MenuIcon-ArtikAralik-v1-preview.png"
cp "$PROJECT_DIR/assets/MenuIcon-GlissandoAnalizi-v1-preview.png" "$CLASS_DIR/ui/MenuIcon-GlissandoAnalizi-v1-preview.png"
javac --release 21 -encoding UTF-8 -cp "$CLASS_DIR:$LIB_CP" -d "$TEST_DIR" "${TEST_SOURCES[@]}"

TEST_CP="$CLASS_DIR:$TEST_DIR:$LIB_CP"
TEST_PREFS_DIR="$BUILD_DIR/test-preferences"
mkdir -p "$TEST_PREFS_DIR"
find "$TEST_PREFS_DIR" -mindepth 1 -delete
# Release tests must never inherit the developer's currently selected UI
# language.  Use a private Turkish test profile so assertions and generated
# DOCX prose remain deterministic without changing the user's MakamLab choice.
typeset -a TEST_JAVA_OPTIONS
TEST_JAVA_OPTIONS=(-Djava.awt.headless=true -Duser.language=tr -Duser.country=TR
  -Djava.util.prefs.userRoot="$TEST_PREFS_DIR")
# Every test source is a standalone regression program with a main method.
# Run the complete suite so the Windows package cannot lag behind the macOS
# application while still passing a small hand-picked subset.
for test_source in "${TEST_SOURCES[@]}"; do
  test_class="${test_source#$PROJECT_DIR/src/test/java/}"
  test_class="${test_class%.java}"
  test_class="${test_class//\//.}"
  print -- "Windows release test: $test_class"
  case "$test_class" in
    com.makambox.studio.ui.AllLocaleButtonFitRegressionTest|\
    com.makambox.studio.ui.AudioRecordingDialogRegressionTest)
      # These release gates create real Swing windows (off-screen) to verify
      # layout, recording-dialog states and native gesture registration.
      # Running them headless would only raise HeadlessException and would not
      # test the product.  The remaining suite stays strictly headless.
      java -Djava.awt.headless=false -Duser.language=tr -Duser.country=TR \
        -Djava.util.prefs.userRoot="$TEST_PREFS_DIR" \
        -cp "$TEST_CP" "$test_class"
      ;;
    com.makambox.studio.ui.TrackpadGestureRegressionTest)
      # This gate validates Apple's com.apple.eawt native magnification API.
      # It is covered by the macOS release; it is deliberately not a Windows
      # release gate because that API does not exist on Windows 10/11.
      print -- "Windows release test skipped (macOS-native only): $test_class"
      ;;
    *)
      java "${TEST_JAVA_OPTIONS[@]}" -cp "$TEST_CP" "$test_class"
      ;;
  esac
done

# This independent source snapshot contains only MakamLab analysis/report
# classes.  Keep a release gate so a future merge cannot reintroduce an
# unrelated Studio/transcription component unnoticed.
cp -R "$CLASS_DIR/." "$PACKAGE_CLASS_DIR/"

jar --create --file "$APP_DIR/Makam.jar" --main-class com.makambox.studio.Makam \
  -C "$PACKAGE_CLASS_DIR" .
if jar --list --file "$APP_DIR/Makam.jar" | grep -Eq \
    '(^|/)(transcription/|MakamsalTranscriptionPanel|StudioFrame|StudioMenuBar|MakamBoxStudio|ClassicTuningToolPanel|LegacyChartDialog|PitchEventsPanel|ReportRenderer|ReportExporter|CsvExporter)'; then
  print -u2 -- "MakamBox Studio transkripsiyon bileşeni Windows MakamLab paketine sızdı."
  exit 1
fi
if [[ "$(jar --list --file "$APP_DIR/Makam.jar" \
    | grep -Ec '^i18n/messages(_[A-Za-z0-9_]+)?\.properties$')" -ne 3 ]]; then
  print -u2 -- "Windows MakamLab paketinde Türkçe, İngilizce veya temel dil kaynağı eksik."
  exit 1
fi
for jar_file in "${LIB_JARS[@]}"; do cp "$jar_file" "$APP_DIR/"; done

JRE_UNPACKED="$BUILD_DIR/jre-unpacked"
mkdir -p "$JRE_UNPACKED"
find "$JRE_UNPACKED" -mindepth 1 -delete
unzip -q "$JRE_ARCHIVE" -d "$JRE_UNPACKED"
JRE_ROOT="$(find "$JRE_UNPACKED" -mindepth 1 -maxdepth 1 -type d | head -1)"
cp -R "$JRE_ROOT" "$STAGE_DIR/runtime"

FFMPEG_UNPACKED="$BUILD_DIR/ffmpeg-unpacked"
mkdir -p "$FFMPEG_UNPACKED"
find "$FFMPEG_UNPACKED" -mindepth 1 -delete
unzip -q "$FFMPEG_ARCHIVE" -d "$FFMPEG_UNPACKED"
FFMPEG_EXE="$(find "$FFMPEG_UNPACKED" -type f -path '*/bin/ffmpeg.exe' | head -1)"
[[ -f "$FFMPEG_EXE" ]] || { print -u2 -- "ffmpeg.exe arşivde bulunamadı"; exit 1; }
cp "$FFMPEG_EXE" "$RESOURCE_DIR/ffmpeg.exe"
FFMPEG_BIN="${FFMPEG_EXE:h}"
typeset -a FFMPEG_DLLS
while IFS= read -r -d $'\0' dll_file; do FFMPEG_DLLS+=("$dll_file"); done \
  < <(find "$FFMPEG_BIN" -maxdepth 1 -type f -iname '*.dll' -print0)
(( ${#FFMPEG_DLLS[@]} > 0 )) || { print -u2 -- "FFmpeg paylaşımlı DLL dosyaları bulunamadı"; exit 1; }
for dll_file in "${FFMPEG_DLLS[@]}"; do cp "$dll_file" "$RESOURCE_DIR/"; done
FFMPEG_LICENSE="$(find "$FFMPEG_UNPACKED" -type f -iname 'LICENSE.txt' | head -1)"
[[ -f "$FFMPEG_LICENSE" ]] && cp "$FFMPEG_LICENSE" "$STAGE_DIR/licenses/FFmpeg-LICENSE.txt"

sips -z 256 256 "$PROJECT_DIR/assets/MakamIcon-1024.png" --out "$RC_DIR/AppIcon.png" >/dev/null
sips -s format ico "$RC_DIR/AppIcon.png" --out "$RC_DIR/AppIcon.ico" >/dev/null
cp "$PROJECT_DIR/windows/windows10-11.manifest" "$RC_DIR/"
cp "$PROJECT_DIR/windows/app.rc" "$RC_DIR/"
cp "$PROJECT_DIR/windows/installer.rc" "$RC_DIR/"
cp "$PROJECT_DIR/windows/uninstaller.rc" "$RC_DIR/"
(
  cd "$RC_DIR"
  "$ZIG" rc /fo app.res app.rc
  "$ZIG" rc /fo installer.res installer.rc
  "$ZIG" rc /fo uninstaller.res uninstaller.rc
)
"$ZIG" cc -target x86_64-windows-gnu -O2 -s -municode -Wl,--subsystem,windows \
  "$PROJECT_DIR/windows/launcher.c" "$RC_DIR/app.res" \
  -o "$STAGE_DIR/MakamLab.exe" -lshell32 -luser32
"$ZIG" cc -target x86_64-windows-gnu -O2 -s -municode -Wl,--subsystem,windows \
  "$PROJECT_DIR/windows/installer.c" "$RC_DIR/installer.res" \
  -o "$BUILD_DIR/installer-stub.exe" -lshell32 -luser32 -lole32
"$ZIG" cc -target x86_64-windows-gnu -O2 -s -municode -Wl,--subsystem,windows \
  "$PROJECT_DIR/windows/uninstaller.c" "$RC_DIR/uninstaller.res" \
  -o "$STAGE_DIR/Uninstall-MakamLab.exe" -lshell32 -luser32
sign_executable "$STAGE_DIR/MakamLab.exe"
sign_executable "$STAGE_DIR/Uninstall-MakamLab.exe"

cp "$PROJECT_DIR/LICENSE" "$STAGE_DIR/LICENSE.txt"
cp "$PROJECT_DIR/MAKAMLAB_WINDOWS_THIRD_PARTY_NOTICES.md" \
  "$STAGE_DIR/THIRD_PARTY_NOTICES.md"
cp "$PROJECT_DIR/windows/WINDOWS-BILGI.txt" "$STAGE_DIR/"
cp "$PROJECT_DIR/licenses/MAKAMBOX-GPL-3.0.txt" \
  "$STAGE_DIR/licenses/MAKAMBOX-GPL-3.0.txt"
cp "$PROJECT_DIR/licenses/JLAYER-LGPL-2.1.txt" \
  "$STAGE_DIR/licenses/JLAYER-LGPL-2.1.txt"
cp "$PROJECT_DIR/licenses/CURVESAPI-BSD-3-CLAUSE.txt" \
  "$STAGE_DIR/licenses/CURVESAPI-BSD-3-CLAUSE.txt"
cp "$PROJECT_DIR/licenses/FFmpeg-LGPL-Notice.txt" \
  "$STAGE_DIR/licenses/FFmpeg-LGPL-Notice.txt"
unzip -p "$PROJECT_DIR/lib/poi-6.0.0.jar" META-INF/LICENSE \
  > "$STAGE_DIR/licenses/APACHE-2.0.txt"
if grep -Eqi 'NeuralNote|MuScriptor|LilyPond|Hacklily|Node\.js' \
    "$STAGE_DIR/THIRD_PARTY_NOTICES.md"; then
  print -u2 -- "Studio ürününe ait lisans kaydı Windows MakamLab paketine sızdı."
  exit 1
fi

[[ -f "$STAGE_DIR/runtime/bin/javaw.exe" ]]
[[ -f "$RESOURCE_DIR/ffmpeg.exe" ]]
[[ -f "$STAGE_DIR/Uninstall-MakamLab.exe" ]]
file "$STAGE_DIR/MakamLab.exe" "$STAGE_DIR/Uninstall-MakamLab.exe" \
  "$RESOURCE_DIR/ffmpeg.exe" "$STAGE_DIR/runtime/bin/javaw.exe"

PAYLOAD_ZIP="$BUILD_DIR/payload.zip"
rm -f "$PAYLOAD_ZIP" "$OUTPUT_PORTABLE" "$OUTPUT_SETUP"
javac --release 21 -d "$BUILD_DIR" \
  "$PROJECT_DIR/tools/PortableZipPacker.java" "$PROJECT_DIR/tools/PayloadPacker.java"
java -cp "$BUILD_DIR" PortableZipPacker "$STAGE_DIR" "$PAYLOAD_ZIP"
cp "$PAYLOAD_ZIP" "$OUTPUT_PORTABLE"
java -cp "$BUILD_DIR" PayloadPacker "$BUILD_DIR/installer-stub.exe" "$PAYLOAD_ZIP" "$OUTPUT_SETUP"
sign_executable "$OUTPUT_SETUP"

unzip -t "$OUTPUT_PORTABLE" >/dev/null
file "$OUTPUT_SETUP"
(
  cd "$DIST_DIR"
  shasum -a 256 "${OUTPUT_SETUP:t}" "${OUTPUT_PORTABLE:t}" > "${OUTPUT_SUMS:t}"
)
shasum -a 256 "$OUTPUT_SETUP" "$OUTPUT_PORTABLE"
print -- "Windows kurulum EXE: $OUTPUT_SETUP"
print -- "Windows taşınabilir ZIP: $OUTPUT_PORTABLE"
print -- "SHA-256: $OUTPUT_SUMS"
