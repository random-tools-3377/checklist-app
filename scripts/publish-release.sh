#!/usr/bin/env bash
# Publishes the signed APKs as one GitHub Release for the version in gradle.properties, with the
# notes in release-notes/<version>.md: the checklist app and Checklist AI, always together and
# always the same version. Does nothing if that version already has a Release, so a push that
# doesn't bump the version publishes nothing.
# The iPhone app's unsigned .ipa and the Windows app's .msi go in too when they were built (a
# missing one doesn't stop the Android release).
# Usage: publish-release.sh checklist.apk checklist-ai.apk [checklist.ipa] [Checklist-<version>.msi]
#   (needs GH_TOKEN, GITHUB_REPOSITORY, GITHUB_SHA; pass "" for a missing .ipa)
set -euo pipefail
apk="$1"
ai_apk="$2"
ipa="${3:-}"
msi="${4:-}"
here="$(cd "$(dirname "$0")" && pwd)"
version="$(sed -n 's/^appVersionName=//p' gradle.properties | tr -d '[:space:]')"
tag="v$version"
notes="release-notes/$version.md"

if gh release view "$tag" --repo "$GITHUB_REPOSITORY" > /dev/null 2>&1; then
  echo "Release $tag already exists. Raise appVersionName in gradle.properties to publish a new one."
  exit 0
fi
# Never publish an older version after a newer one, so the newest stays on top of the list.
newest="$(gh release list --repo "$GITHUB_REPOSITORY" --limit 100 --json tagName --jq '.[].tagName' | sed -n 's/^v//p' | sort -V | tail -n 1)"
if [ -n "$newest" ] && [ "$(printf '%s\n%s\n' "$newest" "$version" | sort -V | tail -n 1)" != "$version" ]; then
  echo "Not publishing $version: $newest is already out."
  exit 0
fi
if [ ! -s "$notes" ]; then
  echo "::error::$notes is missing. Every Release needs notes."
  exit 1
fi

aapt2="$("$here/android-tool.sh" aapt2)"
# Both APKs must be the version being published, and each the app it should be.
check_apk() {
  local file="$1" package="$2" badging found_version found_package
  badging="$("$aapt2" dump badging "$file")"
  found_version="$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<< "$badging" | head -n 1)"
  found_package="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<< "$badging" | head -n 1)"
  if [ "$found_version" != "$version" ]; then
    echo "::error::$file says version '$found_version' but gradle.properties says '$version'."
    exit 1
  fi
  if [ "$found_package" != "$package" ]; then
    echo "::error::$file is '$found_package', not '$package'."
    exit 1
  fi
}
check_apk "$apk" io.github.randomtools3377.checklist
check_apk "$ai_apk" io.github.randomtools3377.checklist.ai

asset="$(dirname "$apk")/checklist-$version.apk"
ai_asset="$(dirname "$ai_apk")/checklist-ai-$version.apk"
cp "$apk" "$asset"
cp "$ai_apk" "$ai_asset"
assets=("$asset" "$ai_asset")

# The iPhone app: its version must match too.
if [ -n "$ipa" ] && [ -s "$ipa" ]; then
  ipa_version="$(python3 - "$ipa" <<'PY'
import plistlib, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    name = next(n for n in z.namelist() if n.endswith(".app/Info.plist") and n.count("/") == 2)
    info = plistlib.loads(z.read(name))
print(info.get("CFBundleShortVersionString", ""), info.get("CFBundleIdentifier", ""))
PY
)"
  if [ "$ipa_version" != "$version io.github.randomtools3377.checklist" ]; then
    echo "::error::$ipa says '$ipa_version', not version $version of io.github.randomtools3377.checklist."
    exit 1
  fi
  ipa_asset="$(dirname "$ipa")/checklist-$version.ipa"
  cp "$ipa" "$ipa_asset"
  assets+=("$ipa_asset")
else
  echo "::warning::No iPhone app this time."
fi

# The Windows app: the build names it after its version, and it must be an .msi (a Windows
# Installer file starts with the bytes D0 CF 11 E0 A1 B1 1A E1).
if [ -n "$msi" ] && [ -s "$msi" ]; then
  if [ "$(basename "$msi")" != "Checklist-$version.msi" ]; then
    echo "::error::$msi is not the Windows app for version $version (Checklist-$version.msi)."
    exit 1
  fi
  if [ "$(head -c 8 "$msi" | od -An -tx1 | tr -d ' \n')" != "d0cf11e0a1b11ae1" ]; then
    echo "::error::$msi is not a Windows Installer file."
    exit 1
  fi
  msi_asset="$(dirname "$msi")/checklist-$version.msi"
  cp "$msi" "$msi_asset"
  assets+=("$msi_asset")
else
  echo "::warning::No Windows app this time."
fi

gh release create "$tag" "${assets[@]}" \
  --repo "$GITHUB_REPOSITORY" \
  --target "$GITHUB_SHA" \
  --title "Checklist $version" \
  --notes-file "$notes"
echo "Published $tag"
