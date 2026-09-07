#!/usr/bin/env bash

# Guards the F-Droid publisher-binary contract.
#
# The merged recipe uses Binaries: plus AllowedAPKSigningKeys, so F-Droid ships OUR
# APK with OUR signature after comparing it against its own build of the tag. Every
# version F-Droid may build must therefore carry an fdroid-flavour APK at the Binaries
# URL, signed with the pinned certificate. A missing or wrongly signed asset fails the
# build on F-Droid's server, publicly, days after the release is cut.
#
# The recipe is read live from fdroiddata master, never from a local copy, so this
# check follows whatever F-Droid actually has.

set -uo pipefail

recipe_url="${RECIPE_URL:-https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/org.astermail.android.yml}"
repo="${TARGET_REPO:-Aster-Privacy/Aster-Android}"
package="${PACKAGE_ID:-org.astermail.android}"
status_url="https://f-droid.org/repo/status/build.json"
work="${FDROID_CHECK_DIR:-${TMPDIR:-/tmp}/fdroid_asset_check}"

mkdir -p "$work"

fail=0
ok()   { printf 'OK    %s\n' "$*"; }
bad()  { printf 'FAIL  %s\n' "$*"; fail=1; }
note() { printf 'note  %s\n' "$*"; }

auth_header=()
if [ -n "${GH_TOKEN:-}" ]; then
  auth_header=(-H "Authorization: Bearer $GH_TOKEN")
fi

apksigner="${APKSIGNER:-}"
if [ -z "$apksigner" ]; then
  for cand in \
    "${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/nonexistent}}"/build-tools/*/apksigner \
    "$HOME"/android-sdk/build-tools/*/apksigner
  do
    [ -x "$cand" ] && apksigner="$cand"
  done
fi
if [ -z "$apksigner" ]; then
  bad "apksigner not found, set APKSIGNER or ANDROID_SDK_ROOT"
  exit 1
fi

if ! curl -fsSL "$recipe_url" -o "$work/recipe.yml"; then
  bad "cannot fetch recipe from $recipe_url"
  exit 1
fi

recipe_vars=$(python3 - "$work/recipe.yml" <<'PY'
import shlex, sys, yaml

recipe = yaml.safe_load(open(sys.argv[1]))
keys = recipe.get("AllowedAPKSigningKeys") or ""
if isinstance(keys, list):
    keys = " ".join(keys)
print("binaries_tpl=" + shlex.quote(str(recipe.get("Binaries") or "").strip()))
print("allowed_keys=" + shlex.quote(str(keys).lower()))
print("current_version=" + shlex.quote(str(recipe.get("CurrentVersion") or "")))
print("current_code=" + shlex.quote(str(recipe.get("CurrentVersionCode") or "")))
PY
) || { bad "cannot parse recipe"; exit 1; }
eval "$recipe_vars"

if [ -z "$binaries_tpl" ]; then
  note "recipe has no Binaries: field, F-Droid signs its own build and this check does not apply"
  exit 0
fi
if [ -z "$allowed_keys" ]; then
  bad "recipe has Binaries: but no AllowedAPKSigningKeys, any signer would be accepted"
fi

latest_tag=$(curl -fsSL "${auth_header[@]}" "https://api.github.com/repos/$repo/releases/latest" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("tag_name",""))') || latest_tag=""
latest_version="${latest_tag#v}"

versions=""
for v in "$current_version" "$latest_version"; do
  [ -n "$v" ] || continue
  case " $versions " in *" $v "*) continue ;; esac
  versions="$versions $v"
done

if [ -z "$versions" ]; then
  bad "no version to check, recipe CurrentVersion and latest release tag both empty"
  exit 1
fi

note "recipe CurrentVersion $current_version ($current_code), latest release $latest_tag"
note "pinned signer $allowed_keys"

for ver in $versions; do
  url="${binaries_tpl//%v/$ver}"
  apk="$work/$ver.apk"
  code=$(curl -sSL -o "$apk" -w '%{http_code}' "$url")
  if [ "$code" != "200" ]; then
    bad "$ver asset missing, HTTP $code for $url"
    continue
  fi
  got=$("$apksigner" verify --print-certs "$apk" 2>/dev/null \
    | grep -i 'SHA-256 digest' | head -1 | grep -oiE '[0-9a-f]{64}' | tr 'A-Z' 'a-z')
  if [ -z "$got" ]; then
    bad "$ver asset is present but unsigned or unreadable by apksigner"
    continue
  fi
  case " $allowed_keys " in
    *" $got "*) ok "$ver asset present and signed by $got" ;;
    *)          bad "$ver asset signed by $got, recipe pins $allowed_keys" ;;
  esac
done

if curl -fsSL "$status_url" -o "$work/build.json"; then
  failed=$(python3 - "$work/build.json" "$package" <<'PY'
import json, sys

status = json.load(open(sys.argv[1]))
hits = [str(code) for app, code in status.get("failedBuilds", []) if app == sys.argv[2]]
print(" ".join(hits))
PY
)
  if [ -n "$failed" ]; then
    bad "F-Droid reports failed builds for $package: versionCode $failed"
  else
    ok "F-Droid reports no failed builds for $package"
  fi
else
  note "could not fetch $status_url, skipping build-status check"
fi

published=$(curl -sS -o /dev/null -w '%{http_code}' "https://f-droid.org/api/v1/packages/$package")
if [ "$published" = "200" ]; then
  note "$package is published on f-droid.org"
else
  note "$package not published yet on f-droid.org (HTTP $published)"
fi

exit "$fail"
