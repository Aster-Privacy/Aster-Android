#!/usr/bin/env bash
# Cut an Aster-Android release to all four channels.
#
#   bash scripts/release.sh 0.6.156            full release
#   bash scripts/release.sh 0.6.156 --dry-run  build and sign, publish nothing
#
# Builds from a clean clone of origin/main so no in-progress work is ever packaged.
# Signing happens locally and never in CI: the keystore stays off the public repo.
#
# Release notes: write them to $work/notes-<version>.md before running.
# Play changelog: fastlane/metadata/android/en-US/changelogs/<versionCode>.txt must
# already be committed on main.
set -euo pipefail

ver="${1:-}"
if [ -z "$ver" ]; then
  echo "usage: bash scripts/release.sh <version> [--dry-run]" >&2
  exit 2
fi
shift
dry_run=0
for a in "$@"; do
  if [ "$a" = "--dry-run" ]; then dry_run=1; fi
done

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="${ASTER_RELEASE_WORKDIR:-$repo_root/../.release_work}"
clone="$work/Aster-Android-$ver"
out_dir="$work/out-$ver"
notes="$work/notes-$ver.md"
cert_sha="88b0a8a6fb94ee73a454a0f92732bd408e92e51ec1e9556744e5f00556100977"

say() { echo; echo "== $* =="; }
die() { echo "FAIL: $*" >&2; exit 1; }

say "preflight"
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${LOCALAPPDATA:-}/Android/Sdk}}"
apksigner=""
bt_ver=""
for bt in 35.0.0 34.0.0; do
  for cand in "$sdk/build-tools/$bt/apksigner.bat" "$sdk/build-tools/$bt/apksigner"; do
    if [ -f "$cand" ] && [ -z "$apksigner" ]; then
      apksigner="$cand"
      bt_ver="$bt"
    fi
  done
done
[ -n "$apksigner" ] || die "apksigner not found under $sdk/build-tools"
command -v gh >/dev/null || die "gh CLI not found"
gh auth status >/dev/null 2>&1 || die "gh is not authenticated"
[ -f "$repo_root/app/google-services.json" ] || die "app/google-services.json missing (gitignored build input)"
[ -f "$repo_root/keystore/aster-mail-upload-v3.jks" ] || die "signing keystore missing"
[ -f "$notes" ] || die "no release notes at $notes"
echo "apksigner: $apksigner (build-tools $bt_ver)"
echo "notes:     $notes"

cur_vc=$(grep -oE 'versionCode = [0-9]+' "$repo_root/app/build.gradle.kts" | grep -oE '[0-9]+' | head -1)
vc="${ASTER_VERSION_CODE:-$((cur_vc + 1))}"
echo "version:   $ver (versionCode $vc, previous $cur_vc)"
if git ls-remote --tags https://github.com/Aster-Privacy/Aster-Android.git "refs/tags/v$ver" | grep -q .; then
  die "tag v$ver already exists on the remote"
fi

say "clean clone"
rm -rf "$clone"
mkdir -p "$work"
git clone -q --branch main https://github.com/Aster-Privacy/Aster-Android.git "$clone"
cd "$clone"
echo "HEAD $(git rev-parse --short HEAD)"

head_sha="$(git rev-parse HEAD)"
if [ "${ASTER_SKIP_CI_CHECK:-0}" != "1" ]; then
  ci=$(gh api "repos/Aster-Privacy/Aster-Android/commits/$head_sha/check-runs"     -q '[.check_runs[] | select(.name != "verify") | "\(.conclusion // "pending") \(.name)"] | .[]' 2>/dev/null || true)
  if echo "$ci" | grep -qE '^(failure|timed_out|cancelled) '; then
    echo "$ci" | grep -E '^(failure|timed_out|cancelled) ' >&2
    die "CI is not green on $head_sha, fix main first or set ASTER_SKIP_CI_CHECK=1"
  fi
  echo "CI on $head_sha: ${ci:-no checks reported}"
fi

cp "$repo_root/app/google-services.json" app/google-services.json
if [ -f "$repo_root/local.properties" ]; then
  cp "$repo_root/local.properties" local.properties
else
  printf 'sdk.dir=%s\n' "$(echo "$sdk" | sed 's|/|\\\\|g; s|^\([A-Za-z]\):|\1\\:|')" > local.properties
fi
mkdir -p keystore
cp "$repo_root/keystore/aster-mail-upload-v3.jks" keystore/
cp "$repo_root/keystore/.upload_v3_password" keystore/
pw_file="$clone/keystore/.upload_v3_password"
KEYSTORE_PASSWORD="$(tr -d '\r\n' < "$pw_file")"
KEY_PASSWORD="$KEYSTORE_PASSWORD"
export KEYSTORE_PASSWORD KEY_PASSWORD

say "version bump"
python - "$ver" "$vc" <<'PY'
import io, re, sys
ver, vc = sys.argv[1], sys.argv[2]
p = "app/build.gradle.kts"
s = io.open(p, encoding="utf-8").read()
s = re.sub(r"versionCode = \d+", "versionCode = " + vc, s, count=1)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "' + ver + '"', s, count=1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
PY

changelog="fastlane/metadata/android/en-US/changelogs/$vc.txt"
[ -f "$changelog" ] || die "missing Play changelog $changelog, commit it on main first"
size=$(wc -c < "$changelog")
[ "$size" -le 500 ] || die "Play changelog is ${size}B, cap is 500B"
echo "Play changelog $changelog is ${size}B"

git add app/build.gradle.kts
git commit -q -m "chore(release): $ver"
git tag -a "v$ver" -m "v$ver"
echo "committed and tagged v$ver"

# Only the full flavor is built here. The fdroid APK must come from the Linux CI
# build so F-Droid can reproduce it: a Windows build never matches their rebuild.
# Never add an fdroid task to this invocation either, because is_fdroid_build is
# true when ANY task name contains "fdroid" and that strips signing from the full
# flavor.
say "build full flavor (signed)"
./gradlew --no-daemon assembleFullRelease bundleFullRelease

full_apk="app/build/outputs/apk/full/release/app-full-release.apk"
aab="app/build/outputs/bundle/fullRelease/app-full-release.aab"
[ -f "$full_apk" ] || die "full APK not produced at $full_apk"
[ -f "$aab" ] || die "AAB not produced at $aab"

rm -rf "$out_dir"
mkdir -p "$out_dir"
cp "$full_apk" "$out_dir/Aster-Mail.apk"
cp "$full_apk" "$out_dir/Aster-Mail-$ver.apk"
cp "$aab" "$out_dir/Aster-Mail-$ver.aab"

say "verify full APK signature"
got=$("$apksigner" verify --print-certs "$out_dir/Aster-Mail.apk" | grep -i "SHA-256 digest" | head -1 | grep -oE '[0-9a-f]{64}')
[ "$got" = "$cert_sha" ] || die "Aster-Mail.apk signer is $got, expected $cert_sha"
echo "  OK Aster-Mail.apk signed by $cert_sha"

if [ "$dry_run" = 1 ]; then
  say "dry run complete"
  echo "artifacts in $out_dir"
  echo "the fdroid APK comes from CI after the tag is pushed, so a dry run skips it"
  echo "nothing pushed, nothing published"
  exit 0
fi

say "push commit and tag"
git push origin main
git push origin "v$ver"

say "fdroid APK from the Linux CI build"
echo "waiting for release_fdroid on v$ver"
run_id=""
for _ in $(seq 1 120); do
  run_id=$(gh run list --repo Aster-Privacy/Aster-Android --workflow release_fdroid.yml     --branch "v$ver" -L 1 --json databaseId,status,conclusion     -q '.[] | select(.status == "completed" and .conclusion == "success") | .databaseId' 2>/dev/null || true)
  [ -n "$run_id" ] && break
  sleep 30
done
[ -n "$run_id" ] || die "release_fdroid did not finish for v$ver, rerun it then sign by hand"
echo "using run $run_id"
ci_dir="$work/ci-fdroid-$ver"
rm -rf "$ci_dir"
mkdir -p "$ci_dir"
gh run download "$run_id" --repo Aster-Privacy/Aster-Android -n fdroid-unsigned --dir "$ci_dir"
ci_apk="$ci_dir/app-fdroid-release-unsigned.apk"
[ -f "$ci_apk" ] || die "CI artifact did not contain app-fdroid-release-unsigned.apk"
ci_vc=$("$sdk/build-tools/$bt_ver/aapt2.exe" dump badging "$ci_apk" 2>/dev/null | head -1 | grep -oE "versionCode='[0-9]+'" | grep -oE '[0-9]+')
[ "$ci_vc" = "$vc" ] || die "CI APK is versionCode $ci_vc, expected $vc"
echo "  OK CI APK is versionCode $ci_vc"

align_flag=""
if [ "$bt_ver" != "34.0.0" ]; then align_flag="--alignment-preserved"; fi
"$apksigner" sign --ks keystore/aster-mail-upload-v3.jks --ks-key-alias aster-mail   --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEYSTORE_PASSWORD   $align_flag --out "$out_dir/Aster-Mail-fdroid-$ver.apk" "$ci_apk"
got=$("$apksigner" verify --print-certs "$out_dir/Aster-Mail-fdroid-$ver.apk" | grep -i "SHA-256 digest" | head -1 | grep -oE '[0-9a-f]{64}')
[ "$got" = "$cert_sha" ] || die "fdroid APK signer is $got, expected $cert_sha"
echo "  OK Aster-Mail-fdroid-$ver.apk signed by $cert_sha"

bad=$(python - "$out_dir/Aster-Mail-fdroid-$ver.apk" <<'PYEOF'
import struct, sys, zipfile
data = open(sys.argv[1], "rb").read()
n = 0
for zi in zipfile.ZipFile(sys.argv[1]).infolist():
    if zi.filename.startswith("META-INF/"):
        continue
    off = zi.header_offset
    nm_len, ex_len = struct.unpack_from("<HH", data, off + 26)
    ex = data[off + 30 + nm_len: off + 30 + nm_len + ex_len]
    i = 0
    while i + 4 <= len(ex):
        hid, sz = struct.unpack_from("<HH", ex, i)
        if hid == 0xd935:
            n += 1
            break
        i += 4 + sz
print(n)
PYEOF
)
[ "$bad" = "0" ] || die "$bad entries carry 0xd935 padding, --alignment-preserved was missed"
echo "  OK alignment preserved ($bad padded entries)"

say "GitHub release"
gh release create "v$ver" --repo Aster-Privacy/Aster-Android --title "v$ver" \
  --notes-file "$notes" \
  "$out_dir/Aster-Mail.apk" "$out_dir/Aster-Mail-$ver.apk" "$out_dir/Aster-Mail-fdroid-$ver.apk"

say "carry APK to the site download target"
mail_tag=$(gh api repos/Aster-Privacy/Aster-Mail/releases/latest -q .tag_name)
gh release upload "$mail_tag" --repo Aster-Privacy/Aster-Mail --clobber "$out_dir/Aster-Mail.apk"
echo "uploaded Aster-Mail.apk to Aster-Mail $mail_tag"

say "Google Play"
play_key="${ASTER_PLAY_SERVICE_ACCOUNT_JSON:-$repo_root/../.ops/play_service_account.json}"
if [ -f "$play_key" ] && command -v fastlane >/dev/null; then
  fastlane supply --aab "$out_dir/Aster-Mail-$ver.aab" --json_key "$play_key" \
    --package_name org.astermail.android --track production --release_status completed \
    --skip_upload_metadata --skip_upload_images --skip_upload_screenshots
  echo "uploaded to Play production"
else
  mkdir -p "$HOME/Downloads"
  cp "$out_dir/Aster-Mail-$ver.aab" "$HOME/Downloads/"
  echo "NOT automated: no Play service account at $play_key, or fastlane is not installed."
  echo "AAB copied to ~/Downloads for manual upload."
  echo "To automate, see scripts/README_release.md."
fi

say "channel audit"
bash "$repo_root/../Claude/scripts/audit_android_channels.sh" "$ver" || true
echo
echo "Release $ver published."
echo "F-Droid picks the tag up on its own build cycle once the recipe is merged."
