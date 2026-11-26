# Building MakamLab from source

## Common prerequisites

- Java 21 JDK with `javac`, `jar`, and `jpackage`
- Python 3
- Git
- approximately 8 GB free disk space for cross-platform package caches

The required Java library JARs are kept in `lib/` so the tagged source can be
rebuilt without introducing an unreviewed dependency resolver. Their licenses
and versions are documented in `THIRD_PARTY_NOTICES.md` and `SBOM.spdx.json`.

## macOS Apple Silicon

Run on an Apple Silicon Mac:

```sh
chmod +x build-makam-macos.sh
./build-makam-macos.sh
```

The script compiles the application, runs release gates, creates the app bundle,
and builds the DMG. Developer ID signing and Apple notarization require the
publisher's own Apple Developer credentials and are not performed by the public
script.

## macOS Universal (Apple Silicon and Intel)

On macOS with Xcode command-line tools, provide both Java 21 JDK homes. Use
matching JDK releases for the two architectures.

```sh
MAKAM_X64_JDK=/path/to/intel-jdk/Contents/Home \
MAKAM_ARM64_JDK=/path/to/arm64-jdk/Contents/Home \
zsh build-makam-macos-universal.sh
```

The script puts the Universal DMG in `build/makam/dist/` and verifies that
the launcher contains both architectures and each bundled Java runtime has
the corresponding architecture. Test installation and launch on each target
architecture before distributing a new package.

## Windows x64 cross-package

The cross-build uses Zig plus the verified Windows JRE and FFmpeg resources
declared by the build script:

```sh
chmod +x build-makam-windows-cross.sh
./build-makam-windows-cross.sh
```

Authenticode signing requires the publisher's own code-signing certificate and
must be performed after package generation.

## Ubuntu amd64 DEB

Docker Desktop or Docker Engine must be running:

```sh
chmod +x build-makam-linux-deb.sh linux/build-deb-in-container.sh
./build-makam-linux-deb.sh
```

The build runs in an Ubuntu 22.04-compatible amd64 container, compiles with Java
21, runs the Linux-compatible regression suite, exercises real MP3/M4A decoding,
installs the generated package in the disposable container, and launches the UI
under Xvfb.

For package-only rebuilding after an already successful full Linux test run:

```sh
MAKAM_LINUX_SKIP_TESTS=1 ./build-makam-linux-deb.sh
```

Do not use the skip option for release qualification.

## Validation files

```sh
python3 tools/validate_report_i18n_keys.py
python3 tools/validate_i18n_isolation.py
python3 tools/generate_sbom.py "$(cat MAKAM_VERSION)" SBOM.spdx.json
```

Only Turkish, English, and the base fallback resource are expected.
