#!/bin/zsh
set -euo pipefail

PROJECT_DIR="${0:A:h}"
IMAGE="${MAKAM_LINUX_BUILD_IMAGE:-eclipse-temurin:21-jdk-jammy}"
PLATFORM="${MAKAM_LINUX_PLATFORM:-linux/amd64}"

command -v docker >/dev/null 2>&1 || {
  print -u2 -- "Ubuntu DEB paketi için Docker bulunamadı."
  exit 1
}
docker info >/dev/null 2>&1 || {
  print -u2 -- "Docker çalışmıyor. Docker Desktop'ı başlatıp yeniden deneyin."
  exit 1
}

docker run --rm --platform "$PLATFORM" \
  -e TZ=Europe/Istanbul \
  -e MAKAM_LINUX_SKIP_TESTS="${MAKAM_LINUX_SKIP_TESTS:-0}" \
  -v "$PROJECT_DIR:/workspace" \
  -w /workspace \
  "$IMAGE" \
  bash /workspace/linux/build-deb-in-container.sh

print -- "Ubuntu DEB çıktıları: $PROJECT_DIR/build/makam/linux-dist"
