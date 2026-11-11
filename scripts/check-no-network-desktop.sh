#!/usr/bin/env bash
# A computer has no internet permission to remove, so for the Windows app "no internet" is checked
# in the code, as for the iPhone: the computer's own code (shared/src/jvmMain and the desktop
# module) may not use any way of reaching the network. The shared code it runs is checked by
# check-no-network-ios.sh. Fails, naming what it found, if it does.
# Usage: check-no-network-desktop.sh
set -euo pipefail
cd "$(dirname "$0")/.."
pattern='java\.net|javax\.net|HttpURLConnection|HttpClient|Socket|URL\(|URI\(|okhttp|ktor'
list="$(git ls-files --cached --others --exclude-standard 'shared/src/jvmMain/*' 'desktop/src/*' 'desktop/build.gradle.kts' | grep -E '\.(kt|kts|java)$' || true)"
count="$(printf '%s\n' "$list" | grep -c . || true)"
if [ "$count" -lt 1 ]; then
  echo "::error::No desktop source files found to check."
  exit 1
fi
found="$(printf '%s\n' "$list" | tr '\n' '\0' | xargs -0 grep -nE "$pattern" || true)"
if [ -n "$found" ]; then
  echo "::error::Networking code in the Windows app's code, which must not reach the internet:"
  echo "$found"
  exit 1
fi
echo "No networking code in $count Windows app source files."
