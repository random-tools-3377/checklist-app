#!/usr/bin/env bash
# Fails unless the APK turns phone backup off (android:allowBackup="false") and its backup rules
# really leave out everything: the data-extraction rules (Android 12 and later) exclude every
# domain for cloud backup and for device transfer, and the older full-backup rules exclude every
# domain too. The rules are read from the built APK, not the source, as the manifest is.
# Usage: check-backup.sh app.apk
set -euo pipefail
apk="$1"
here="$(cd "$(dirname "$0")" && pwd)"
aapt2="$("$here/android-tool.sh" aapt2)"

manifest="$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk")"
# grep reads the text directly, not through a pipe: grep -q stops at the first match, and with
# pipefail a writer cut off mid-pipe would count as a failure.
grep -E 'android:(allowBackup|dataExtractionRules|fullBackupContent)' <<< "$manifest" || true

if ! grep -qE 'android:allowBackup\([^)]*\)=(false|0x0+\b)' <<< "$manifest"; then
  echo "::error::android:allowBackup is not false in the APK."
  exit 1
fi
if ! grep -qE 'android:dataExtractionRules\(' <<< "$manifest"; then
  echo "::error::The APK has no data-extraction rules."
  exit 1
fi
if ! grep -qE 'android:fullBackupContent\(' <<< "$manifest"; then
  echo "::error::The APK has no full-backup rules for Android 11 and earlier."
  exit 1
fi

resources="$("$aapt2" dump resources "$apk")"

# The file in the APK that a manifest attribute points at. The manifest names it by resource id
# (like @0x7f150002); the resource table says which file that is, wherever the build put it.
rules_file() {
  local attribute="$1" id file
  id="$(sed -nE "s/.*android:${attribute}\\([^)]*\\)=@(0x[0-9a-fA-F]+).*/\\1/p" <<< "$manifest" | head -n 1)"
  if [ -z "$id" ]; then
    echo "::error::android:${attribute} does not point at a rules file." >&2
    return 1
  fi
  file="$(awk -v id="$id" '
    $1 == "resource" { found = (tolower($2) == tolower(id)) ; next }
    found && /\(file\)/ { for (i = 1; i < NF; i++) if ($i == "(file)") { print $(i + 1); exit } }
  ' <<< "$resources")"
  # Failing that, the place the build usually puts it.
  if [ -z "$file" ] && "$aapt2" dump xmltree --file "$2" "$apk" > /dev/null 2>&1; then
    file="$2"
  fi
  if [ -z "$file" ]; then
    echo "::error::The rules file for android:${attribute} ($id) is not in the APK." >&2
    return 1
  fi
  echo "$file"
}

check_rules() {
  local attribute="$1" mode="$2" usual="$3" file tree
  file="$(rules_file "$attribute" "$usual")"
  tree="$("$aapt2" dump xmltree --file "$file" "$apk")"
  if ! "$here/check-extraction-rules.sh" "$mode" <<< "$tree"; then
    echo "::error::$file (android:${attribute}) does not leave out everything."
    exit 1
  fi
  echo "$file excludes every domain."
}

check_rules dataExtractionRules rules res/xml/data_extraction_rules.xml
check_rules fullBackupContent legacy res/xml/backup_rules.xml
echo "Backup check passed: phone backup is off, and the rules leave out everything."
