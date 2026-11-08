#!/usr/bin/env bash
# Fails unless the APK is signed by exactly one certificate, the app's release certificate.
# A build signed with a debug key, or not signed, never passes.
# Only the digests and the verification result are printed: the certificate's name fields
# are left out of the public log.
# Usage: check-signature.sh app.apk AA:BB:...   (the expected SHA-256)
set -euo pipefail
apk="$1"
expected="$(printf '%s' "$2" | tr -d ': ' | tr 'A-F' 'a-f')"

here="$(cd "$(dirname "$0")" && pwd)"
apksigner="$("$here/android-tool.sh" apksigner)"
report="$(mktemp)"
trap 'rm -f "$report"' EXIT

if ! "$apksigner" verify --verbose --print-certs "$apk" > "$report" 2>&1; then
  grep -E '^(ERROR|DOES NOT VERIFY)' "$report" || true
  echo "::error::$apk is not validly signed."
  exit 1
fi

grep -E '^(Verifies|Verified using|Number of signers|Signer #[0-9]+ certificate SHA-256 digest)' "$report" || true

if ! grep -qE '^Verified using v(2|3) scheme .*: true' "$report"; then
  echo "::error::$apk is not signed with APK Signature Scheme v2 or v3."
  exit 1
fi

digests="$(grep -E '^Signer #[0-9]+ certificate SHA-256 digest:' "$report" | sed 's/.*digest: *//' | tr -d ': ' | tr 'A-F' 'a-f')"
count="$(printf '%s\n' "$digests" | grep -c . || true)"
if [ "$count" != "1" ]; then
  echo "::error::Expected exactly one signer, found $count."
  exit 1
fi
if [ "$digests" != "$expected" ]; then
  echo "::error::$apk is signed with the wrong certificate (SHA-256 $digests). It must be signed with the release key."
  exit 1
fi
echo "Signature check passed: signed with the release certificate."
