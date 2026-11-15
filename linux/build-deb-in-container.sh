#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR=/workspace
APP_VERSION="$(tr -d '[:space:]' < "$PROJECT_DIR/MAKAM_VERSION")"
BUILD_DIR="$PROJECT_DIR/build/makam/linux-amd64"
CLASS_DIR="$BUILD_DIR/classes"
TEST_DIR="$BUILD_DIR/test-classes"
PACKAGE_CLASS_DIR="$BUILD_DIR/package-classes"
PACKAGE_DIR="$BUILD_DIR/package"
DIST_DIR="$PROJECT_DIR/build/makam/linux-dist"
TEST_PREFS_DIR="$BUILD_DIR/test-preferences"
JPACKAGE_TEMP="$BUILD_DIR/jpackage-temp"
APP_NAME="MakamLab"
PACKAGE_NAME="makamlab"
FINAL_DEB="$DIST_DIR/MakamLab-$APP_VERSION-Ubuntu-amd64.deb"
SUM_FILE="$DIST_DIR/MakamLab-$APP_VERSION-Ubuntu-amd64-SHA256SUMS.txt"

export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y --no-install-recommends \
  binutils desktop-file-utils fakeroot ffmpeg file fontconfig gnome-menus python3 unzip \
  libasound2 libfreetype6 libgtk-3-0 libx11-6 libxext6 libxi6 \
  libxrender1 libxtst6 xauth xdg-utils xvfb >/dev/null

python3 "$PROJECT_DIR/tools/validate_report_i18n_keys.py"
python3 "$PROJECT_DIR/tools/validate_i18n_isolation.py"

mkdir -p "$CLASS_DIR" "$TEST_DIR" "$PACKAGE_CLASS_DIR" "$PACKAGE_DIR" \
  "$DIST_DIR" "$TEST_PREFS_DIR" "$JPACKAGE_TEMP"
find "$CLASS_DIR" "$TEST_DIR" "$PACKAGE_CLASS_DIR" "$PACKAGE_DIR" \
  "$TEST_PREFS_DIR" "$JPACKAGE_TEMP" -mindepth 1 -delete
find "$DIST_DIR" -mindepth 1 -delete

mapfile -d '' LIB_JARS < <(find "$PROJECT_DIR/lib" -maxdepth 1 -name '*.jar' \
  ! -name '._*' -print0 | sort -z)
mapfile -d '' MAIN_SOURCES < <(find "$PROJECT_DIR/src/main/java" -name '*.java' \
  ! -name '._*' -print0 | sort -z)
mapfile -d '' TEST_SOURCES < <(find "$PROJECT_DIR/src/test/java" -name '*.java' \
  ! -name '._*' -print0 | sort -z)
LIB_CP="$(IFS=:; echo "${LIB_JARS[*]}")"

javac --release 21 -encoding UTF-8 -cp "$LIB_CP" -d "$CLASS_DIR" "${MAIN_SOURCES[@]}"
cp -R "$PROJECT_DIR/src/main/resources/." "$CLASS_DIR/"
mkdir -p "$CLASS_DIR/ui"
cp "$PROJECT_DIR/assets/MakamIcon-1024.png" "$CLASS_DIR/ui/MakamIcon-1024.png"
cp "$PROJECT_DIR/assets/MakamLab-Wordmark-v1.png" "$CLASS_DIR/ui/MakamLab-Wordmark-v1.png"
cp "$PROJECT_DIR/assets/MenuIcon-PerdeAnalizi-v1.png" "$CLASS_DIR/ui/MenuIcon-PerdeAnalizi-v1.png"
cp "$PROJECT_DIR/assets/MenuIcon-IniciCikici-v1.png" "$CLASS_DIR/ui/MenuIcon-IniciCikici-v1.png"
cp "$PROJECT_DIR/assets/MenuIcon-ArtikAralik-v1-preview.png" \
  "$CLASS_DIR/ui/MenuIcon-ArtikAralik-v1-preview.png"
cp "$PROJECT_DIR/assets/MenuIcon-GlissandoAnalizi-v1-preview.png" \
  "$CLASS_DIR/ui/MenuIcon-GlissandoAnalizi-v1-preview.png"
javac --release 21 -encoding UTF-8 -cp "$CLASS_DIR:$LIB_CP" \
  -d "$TEST_DIR" "${TEST_SOURCES[@]}"

TEST_CP="$CLASS_DIR:$TEST_DIR:$LIB_CP"
COMMON_TEST_OPTIONS=(
  -Duser.language=tr
  -Duser.country=TR
  -Djava.util.prefs.userRoot="$TEST_PREFS_DIR"
)

if [[ "${MAKAM_LINUX_SKIP_TESTS:-0}" != "1" ]]; then
  # Run every platform-neutral regression program on the actual Linux amd64
  # runtime. The two window-layout gates use Xvfb; Apple's gesture bridge is a
  # macOS-only release gate and is intentionally skipped here.
  for test_source in "${TEST_SOURCES[@]}"; do
    test_class="${test_source#"$PROJECT_DIR/src/test/java/"}"
    test_class="${test_class%.java}"
    test_class="${test_class//\//.}"
    echo "Ubuntu release test: $test_class"
    case "$test_class" in
      com.makambox.studio.ui.TrackpadGestureRegressionTest)
        echo "Ubuntu release test skipped (macOS-native only): $test_class"
        ;;
      com.makambox.studio.ui.AllLocaleButtonFitRegressionTest|\
      com.makambox.studio.ui.AudioRecordingDialogRegressionTest)
        xvfb-run -a java -Djava.awt.headless=false "${COMMON_TEST_OPTIONS[@]}" \
          -cp "$TEST_CP" "$test_class"
        ;;
      *)
        java -Djava.awt.headless=true "${COMMON_TEST_OPTIONS[@]}" \
          -cp "$TEST_CP" "$test_class"
        ;;
    esac
  done

  # Exercise the real Linux FFmpeg path for both compressed formats. These
  # fixtures are generated inside Linux rather than using an injected decoder.
  FIXTURE_DIR="$BUILD_DIR/audio-fixtures"
  mkdir -p "$FIXTURE_DIR"
  ffmpeg -hide_banner -loglevel error -y -f lavfi \
    -i 'sine=frequency=220:duration=2:sample_rate=44100' -c:a aac \
    "$FIXTURE_DIR/linux-karar.m4a"
  ffmpeg -hide_banner -loglevel error -y -f lavfi \
    -i 'sine=frequency=220:duration=2:sample_rate=44100' -c:a libmp3lame \
    "$FIXTURE_DIR/linux-karar.mp3"
  java -Djava.awt.headless=true "${COMMON_TEST_OPTIONS[@]}" -cp "$TEST_CP" \
    com.makambox.studio.audio.AudioLoaderM4aRegressionTest \
    "$FIXTURE_DIR/linux-karar.m4a"
  java -Djava.awt.headless=true "${COMMON_TEST_OPTIONS[@]}" -cp "$TEST_CP" \
    com.makambox.studio.audio.AudioLoaderMp3RegressionTest \
    "$FIXTURE_DIR/linux-karar.mp3"
fi

cp -R "$CLASS_DIR/." "$PACKAGE_CLASS_DIR/"
jar --create --file "$PACKAGE_DIR/Makam.jar" \
  --main-class com.makambox.studio.Makam -C "$PACKAGE_CLASS_DIR" .
if jar --list --file "$PACKAGE_DIR/Makam.jar" | grep -Eq \
  '(^|/)(transcription/|MakamsalTranscriptionPanel|StudioFrame|StudioMenuBar|MakamBoxStudio|ClassicTuningToolPanel|LegacyChartDialog|PitchEventsPanel|ReportRenderer|ReportExporter|CsvExporter)'; then
  echo "MakamBox Studio bileşeni Ubuntu MakamLab paketine sızdı." >&2
  exit 1
fi
if [[ "$(jar --list --file "$PACKAGE_DIR/Makam.jar" \
    | grep -Ec '^i18n/messages(_[A-Za-z0-9_]+)?\.properties$')" -ne 3 ]]; then
  echo "Ubuntu paketinde Türkçe, İngilizce veya temel dil kaynağı eksik." >&2
  exit 1
fi
cp "${LIB_JARS[@]}" "$PACKAGE_DIR/"
mkdir -p "$PACKAGE_DIR/legal/licenses"
cp "$PROJECT_DIR/LICENSE" "$PACKAGE_DIR/legal/LICENSE.txt"
cp "$PROJECT_DIR/MAKAMLAB_LINUX_THIRD_PARTY_NOTICES.md" \
  "$PACKAGE_DIR/legal/THIRD_PARTY_NOTICES.md"
cp "$PROJECT_DIR/linux/MAKAMLAB-LINUX-BILGI.txt" "$PACKAGE_DIR/legal/"
cp "$PROJECT_DIR/licenses/MAKAMBOX-GPL-3.0.txt" \
  "$PACKAGE_DIR/legal/licenses/MAKAMBOX-GPL-3.0.txt"
cp "$PROJECT_DIR/licenses/JLAYER-LGPL-2.1.txt" \
  "$PACKAGE_DIR/legal/licenses/JLAYER-LGPL-2.1.txt"
cp "$PROJECT_DIR/licenses/CURVESAPI-BSD-3-CLAUSE.txt" \
  "$PACKAGE_DIR/legal/licenses/CURVESAPI-BSD-3-CLAUSE.txt"
unzip -p "$PROJECT_DIR/lib/poi-6.0.0.jar" META-INF/LICENSE \
  > "$PACKAGE_DIR/legal/licenses/APACHE-2.0.txt"

rm -f "$DIST_DIR"/*.deb
jpackage --type deb \
  --name "$APP_NAME" \
  --linux-package-name "$PACKAGE_NAME" \
  --linux-app-release 1 \
  --app-version "$APP_VERSION" \
  --vendor "MakamLab Academic" \
  --linux-deb-maintainer "emrepinarbasi@gmail.com" \
  --description "Türk makam müziği için akademik perde, yön, aralık ve glissando analizi" \
  --input "$PACKAGE_DIR" \
  --main-jar Makam.jar \
  --main-class com.makambox.studio.Makam \
  --icon "$PROJECT_DIR/assets/MakamIcon-1024.png" \
  --license-file "$PROJECT_DIR/LICENSE" \
  --linux-package-deps "ffmpeg" \
  --linux-menu-group "AudioVideo" \
  --linux-app-category "AudioVideo" \
  --linux-shortcut \
  --java-options "-Dfile.encoding=UTF-8" \
  --java-options "-Xmx2g" \
  --dest "$DIST_DIR" \
  --temp "$JPACKAGE_TEMP"

GENERATED_DEB="$(find "$DIST_DIR" -maxdepth 1 -type f -name '*.deb' | head -1)"
[[ -f "$GENERATED_DEB" ]] || { echo "jpackage DEB üretmedi." >&2; exit 1; }
mv "$GENERATED_DEB" "$FINAL_DEB"

dpkg-deb --info "$FINAL_DEB"
dpkg-deb --contents "$FINAL_DEB" > "$DIST_DIR/MakamLab-$APP_VERSION-Ubuntu-amd64-CONTENTS.txt"
[[ "$(dpkg-deb -f "$FINAL_DEB" Architecture)" == "amd64" ]]
DEPENDS="$(dpkg-deb -f "$FINAL_DEB" Depends)"
grep -Eq '(^|, )ffmpeg([, ]|$)' <<<"$DEPENDS"
grep -Eq '(^|, )xdg-utils([, ]|$)' <<<"$DEPENDS"

EXTRACT_DIR="$BUILD_DIR/deb-extracted"
mkdir -p "$EXTRACT_DIR"
find "$EXTRACT_DIR" -mindepth 1 -delete
dpkg-deb -x "$FINAL_DEB" "$EXTRACT_DIR"
PACKAGED_JAR="$(find "$EXTRACT_DIR/opt" -type f -name Makam.jar | head -1)"
PACKAGED_LAUNCHER="$(find "$EXTRACT_DIR/opt/makamlab/bin" -maxdepth 1 \
  -type f -perm -111 | head -1)"
PACKAGED_RUNTIME="$EXTRACT_DIR/opt/makamlab/lib/runtime"
[[ -f "$PACKAGED_JAR" && -x "$PACKAGED_LAUNCHER" ]]
[[ -f "$PACKAGED_RUNTIME/lib/modules" \
  && -f "$PACKAGED_RUNTIME/lib/libjli.so" \
  && -f "$PACKAGED_RUNTIME/lib/server/libjvm.so" ]]
file "$PACKAGED_LAUNCHER" | grep -Eq 'ELF 64-bit.*x86-64'
[[ "$(jar --list --file "$PACKAGED_JAR" \
    | grep -Ec '^i18n/messages(_[A-Za-z0-9_]+)?\.properties$')" -eq 3 ]]

DESKTOP_FILE="$(find "$EXTRACT_DIR" -type f -name '*.desktop' | head -1)"
[[ -f "$DESKTOP_FILE" ]]
desktop-file-validate "$DESKTOP_FILE"

# Install the just-built package in the disposable Ubuntu container and prove
# that its bundled runtime can keep the Swing application alive under Xvfb.
dpkg -i "$FINAL_DEB" >/dev/null
LAUNCHER="$(find /opt/makamlab/bin -maxdepth 1 -type f -perm -111 | head -1)"
[[ -x "$LAUNCHER" ]]
set +e
timeout 12s xvfb-run -a "$LAUNCHER" >"$BUILD_DIR/gui-smoke.log" 2>&1
SMOKE_STATUS=$?
set -e
if [[ "$SMOKE_STATUS" -ne 124 ]]; then
  cat "$BUILD_DIR/gui-smoke.log" >&2
  echo "Ubuntu GUI açılış testi başarısız (kod=$SMOKE_STATUS)." >&2
  exit 1
fi

(
  cd "$DIST_DIR"
  sha256sum "$(basename "$FINAL_DEB")" > "$(basename "$SUM_FILE")"
)
echo "Ubuntu DEB: $FINAL_DEB"
echo "SHA-256: $SUM_FILE"
