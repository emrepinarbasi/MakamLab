#!/bin/zsh
set -euo pipefail

PROJECT_DIR="${0:A:h}"
BUILD_DIR="$PROJECT_DIR/build/makam"
CLASS_DIR="$BUILD_DIR/classes"
TEST_DIR="$BUILD_DIR/test-classes"
PACKAGE_DIR="$BUILD_DIR/package"
PACKAGE_CLASS_DIR="$BUILD_DIR/package-classes"
DIST_DIR="$BUILD_DIR/dist"
ICONSET_DIR="$BUILD_DIR/Makam.iconset"
APP_VERSION="$(tr -d '[:space:]' < "$PROJECT_DIR/MAKAM_VERSION")"
APP_DISPLAY_NAME="MakamLab"
APP_INTERNAL_NAME="MakamLab"
APP_BUNDLE="$DIST_DIR/$APP_INTERNAL_NAME.app"
LEGACY_UNICODE_APP_BUNDLE="$DIST_DIR/$APP_DISPLAY_NAME.app"
JPACKAGE_DMG="$DIST_DIR/MakamLab-$APP_VERSION.dmg"
FINAL_DMG="$DIST_DIR/MakamLab-V$APP_VERSION-macOS-Apple-Silicon-arm64.dmg"
FINAL_ZIP="$DIST_DIR/MakamLab-$APP_VERSION-macOS-Apple-Silicon-arm64-APP.zip"
JPACKAGE_BIN="${JPACKAGE_BIN:-$(command -v jpackage)}"

HOST_ARCH="$(uname -m)"
if [[ "$HOST_ARCH" != "arm64" && "${UNIVERSAL_STAGE:-0}" != "1" ]]; then
  print -u2 -- "MakamLab macOS paketi Apple Silicon üzerinde üretilmelidir."
  exit 1
fi

# A bilingual release must never mix Turkish and English inside one selected
# interface/report. The gate also protects makam/perde/person/product proper
# names from translation or transliteration.
python3 "$PROJECT_DIR/tools/validate_report_i18n_keys.py"
python3 "$PROJECT_DIR/tools/validate_i18n_isolation.py"

mkdir -p "$CLASS_DIR" "$TEST_DIR" "$PACKAGE_DIR" "$PACKAGE_CLASS_DIR" "$DIST_DIR" "$ICONSET_DIR"
find "$CLASS_DIR" -mindepth 1 -delete
find "$TEST_DIR" -mindepth 1 -delete
find "$PACKAGE_DIR" -mindepth 1 -delete
find "$PACKAGE_CLASS_DIR" -mindepth 1 -delete
find "$ICONSET_DIR" -mindepth 1 -delete

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

# This independent source snapshot contains only MakamLab analysis/report
# classes.  Keep a release gate so a future merge cannot reintroduce an
# unrelated Studio/transcription component unnoticed.
cp -R "$CLASS_DIR/." "$PACKAGE_CLASS_DIR/"

jar --create --file "$PACKAGE_DIR/Makam.jar" \
  --main-class com.makambox.studio.Makam -C "$PACKAGE_CLASS_DIR" .
if jar --list --file "$PACKAGE_DIR/Makam.jar" | grep -Eq \
    '(^|/)(transcription/|MakamsalTranscriptionPanel|StudioFrame|StudioMenuBar|MakamBoxStudio)'; then
  print -u2 -- "MakamBox Studio transkripsiyon bileşeni MakamLab paketine sızdı."
  exit 1
fi
for jar_file in "${LIB_JARS[@]}"; do cp "$jar_file" "$PACKAGE_DIR/"; done

sips -z 1024 1024 "$PROJECT_DIR/assets/MakamIcon-1024.png" \
  --out "$BUILD_DIR/MakamIcon-1024.png" >/dev/null
for spec in \
    "16 icon_16x16" "32 icon_16x16@2x" "32 icon_32x32" "64 icon_32x32@2x" \
    "128 icon_128x128" "256 icon_128x128@2x" "256 icon_256x256" \
    "512 icon_256x256@2x" "512 icon_512x512" "1024 icon_512x512@2x"; do
  set -- $=spec
  sips -z "$1" "$1" "$BUILD_DIR/MakamIcon-1024.png" --out "$ICONSET_DIR/$2.png" >/dev/null
done
iconutil -c icns "$ICONSET_DIR" -o "$BUILD_DIR/Makam.icns"

rm -rf "$APP_BUNDLE" "$LEGACY_UNICODE_APP_BUNDLE"
"$JPACKAGE_BIN" --type app-image --name "$APP_INTERNAL_NAME" --app-version "$APP_VERSION" \
  --vendor "MakamLab Academic" --mac-package-identifier com.makamlab.academic \
  --input "$PACKAGE_DIR" --main-jar Makam.jar --main-class com.makambox.studio.Makam \
  --icon "$BUILD_DIR/Makam.icns" --dest "$DIST_DIR" \
  --java-options "-Dfile.encoding=UTF-8" --java-options "-Xmx2g" \
  --java-options "--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED"

PLIST="$APP_BUNDLE/Contents/Info.plist"
plutil -replace CFBundleName -string "$APP_DISPLAY_NAME" "$PLIST"
plutil -insert CFBundleDisplayName -string "$APP_DISPLAY_NAME" "$PLIST"
plutil -replace NSHighResolutionCapable -bool YES "$PLIST"
plutil -replace LSMinimumSystemVersion -string "13.5" "$PLIST"
plutil -replace LSArchitecturePriority -json "[\"$HOST_ARCH\"]" "$PLIST"
plutil -replace LSRequiresNativeExecution -bool YES "$PLIST"
plutil -lint "$PLIST"

mkdir -p "$APP_BUNDLE/Contents/Resources/licenses"
cp "$PROJECT_DIR/LICENSE" "$APP_BUNDLE/Contents/Resources/LICENSE"
cp "$PROJECT_DIR/MAKAMLAB_THIRD_PARTY_NOTICES.md" \
  "$APP_BUNDLE/Contents/Resources/THIRD_PARTY_NOTICES.md"
# MakamLab is a focused analysis/report product.  Do not copy the unrelated
# MakamBox Studio transcription notices into this distribution.
cp "$PROJECT_DIR/licenses/MAKAMBOX-GPL-3.0.txt" \
  "$APP_BUNDLE/Contents/Resources/licenses/MAKAMBOX-GPL-3.0.txt"
cp "$PROJECT_DIR/licenses/JLAYER-LGPL-2.1.txt" \
  "$APP_BUNDLE/Contents/Resources/licenses/JLAYER-LGPL-2.1.txt"
cp "$PROJECT_DIR/licenses/CURVESAPI-BSD-3-CLAUSE.txt" \
  "$APP_BUNDLE/Contents/Resources/licenses/CURVESAPI-BSD-3-CLAUSE.txt"
# Use the Apache licence embedded in the POI dependency itself so MakamLab's
# legal inventory remains independent of the separate Studio product.
unzip -p "$PROJECT_DIR/lib/poi-6.0.0.jar" META-INF/LICENSE \
  > "$APP_BUNDLE/Contents/Resources/licenses/APACHE-2.0.txt"
if grep -Eqi 'NeuralNote|MuScriptor|LilyPond|Hacklily|Node\.js|FFmpeg' \
    "$APP_BUNDLE/Contents/Resources/THIRD_PARTY_NOTICES.md"; then
  print -u2 -- "Studio ürününe ait lisans kaydı MakamLab bildirimine sızdı."
  exit 1
fi

# Ad-hoc test builds cannot satisfy hardened-runtime library validation because
# the bundled JDK has no shared Developer-ID TeamIdentifier.  Sign the complete
# bundle consistently without the runtime flag; release builds should replace
# this with Developer ID Application + hardened runtime + notarization.
codesign --force --deep --sign - "$APP_BUNDLE"
codesign --verify --deep --strict --verbose=2 "$APP_BUNDLE"
if [[ "${UNIVERSAL_STAGE:-0}" == "1" ]]; then exit 0; fi
rm -f "$FINAL_ZIP"
(
  cd "$DIST_DIR"
  COPYFILE_DISABLE=1 zip -qry -y "$FINAL_ZIP" "$APP_INTERNAL_NAME.app"
)
cp "$PROJECT_DIR/assets/MakamIcon-1024.png" "$DIST_DIR/MakamLab-App-Icon.png"

if [[ "${APP_ONLY:-0}" != "1" ]]; then
  rm -f "$JPACKAGE_DMG" "$FINAL_DMG"
  "$JPACKAGE_BIN" --type dmg --name "$APP_DISPLAY_NAME" --app-version "$APP_VERSION" \
    --vendor "MakamLab Academic" --mac-package-identifier com.makamlab.academic \
    --app-image "$APP_BUNDLE" --icon "$BUILD_DIR/Makam.icns" \
    --license-file "$PROJECT_DIR/LICENSE" --dest "$DIST_DIR"
  mv "$JPACKAGE_DMG" "$FINAL_DMG"
  codesign --force --sign - "$FINAL_DMG"
  codesign --verify --verbose=2 "$FINAL_DMG"
  hdiutil verify "$FINAL_DMG"
fi

print -- "MakamLab uygulaması: $APP_BUNDLE"
print -- "MakamLab uygulama ZIP'i: $FINAL_ZIP"
[[ "${APP_ONLY:-0}" == "1" ]] || print -- "MakamLab V1 DMG: $FINAL_DMG"
