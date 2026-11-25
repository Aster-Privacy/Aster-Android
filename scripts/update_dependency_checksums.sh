#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

metadata=gradle/verification-metadata.xml
gradle_flags=(--no-daemon --max-workers=2 --write-verification-metadata sha256)

ASTER_UNSIGNED=1 ./gradlew "${gradle_flags[@]}" \
  assembleFullRelease bundleFullRelease assembleFullDebug testFullDebugUnitTest
./gradlew "${gradle_flags[@]}" \
  assembleFdroidRelease assembleFdroidDebug testFdroidDebugUnitTest

aapt2_version=$(sed -n 's/.*name="aapt2-proto" version="\([^"]*\)".*/\1/p' "$metadata" | sort -u | tail -n 1)
if [ -z "$aapt2_version" ]; then
  echo "aapt2 version not found in $metadata" >&2
  exit 1
fi

base="https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/${aapt2_version}"
python3 scripts/add_aapt2_checksums.py "$metadata" "$aapt2_version" "$base"

echo "Updated $metadata. Review the diff, then commit it with the dependency change."
