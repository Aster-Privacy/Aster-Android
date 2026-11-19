#!/usr/bin/env bash
# Build the unsigned fdroid flavor inside the F-Droid buildserver image.
#
#   bash scripts/build_fdroid_in_container.sh <git ref> <output dir>
#
# F-Droid rebuilds the fdroid flavor on Linux and compares it byte for byte with the
# APK we publish, so the published APK has to come from that same Linux toolchain.
# This clones the local repository at the given ref inside the same image the
# fdroiddata pipeline uses, runs gradlew-fdroid exactly as F-Droid does, and copies
# app-fdroid-release-unsigned.apk to the output directory.
set -euo pipefail

ref="${1:-}"
out="${2:-}"
if [ -z "$ref" ] || [ -z "$out" ]; then
  echo "usage: bash scripts/build_fdroid_in_container.sh <git ref> <output dir>" >&2
  exit 2
fi

pinned_image="registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie@sha256:1ea8bef1820bff06c2974e31b4fc4c79c364cd24231b86e71fa4b926797e0dac"
image="${ASTER_FDROID_IMAGE:-$pinned_image}"
case "$image" in
  *@sha256:[0-9a-f]*) ;;
  *) echo "ASTER_FDROID_IMAGE must name an image by digest (name@sha256:...): $image" >&2; exit 2 ;;
esac
image_digest="${image##*@}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$out"
out="$(cd "$out" && pwd)"

to_docker_path() {
  case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) cygpath -m "$1" ;;
    *) printf '%s' "$1" ;;
  esac
}

command -v docker >/dev/null || { echo "docker not found" >&2; exit 1; }
docker pull "$image"
pulled_digests="$(docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$image")"
printf '%s\n' "$pulled_digests" | grep -qF "@$image_digest" || { echo "pulled image digest does not match $image_digest" >&2; exit 1; }
echo "fdroid build image: $image"

MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' docker run --rm -i \
  -v "$(to_docker_path "$repo_root"):/src:ro" \
  -v "$(to_docker_path "$out"):/out" \
  -e "ref=$ref" \
  "$image" bash -s <<'IN_CONTAINER'
set -euo pipefail
source /etc/profile.d/bsenv.sh
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export HOME=/home/vagrant
export GRADLE_USER_HOME=/home/vagrant/.gradle
export CACHEDIR=/home/vagrant/.cache/gradlew-fdroid
mkdir -p "$GRADLE_USER_HOME" "$CACHEDIR"
git config --global --add safe.directory '*'
git clone -q /src /work/src
cd /work/src
git checkout -q "$ref"
echo "building $(git rev-parse HEAD) inside $(. /etc/os-release && echo "$PRETTY_NAME") with $(java -version 2>&1 | head -1)"
rm -f gradle/wrapper/gradle-wrapper.jar
cd app
gradlew-fdroid assembleFdroidRelease
cp build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk /out/app-fdroid-release-unsigned.apk
IN_CONTAINER

[ -f "$out/app-fdroid-release-unsigned.apk" ] || { echo "container build produced no APK" >&2; exit 1; }
echo "fdroid flavor built in $image: $out/app-fdroid-release-unsigned.apk"
