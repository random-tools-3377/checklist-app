#!/usr/bin/env bash
# The iPhone has no internet permission to remove, so there "no internet" is checked in the code:
# the shared code and the iPhone app may not use any way of reaching the network. With the
# built app's binary as an argument, it also checks the app links none of the iPhone's
# networking classes. Fails, naming what it found, if they do.
# Usage: check-no-network-ios.sh [path/to/Checklist.app/Checklist]
set -euo pipefail
cd "$(dirname "$0")/.."
pattern='NSURLSession|URLSession|NSURLConnection|NWConnection|NWPathMonitor|CFSocket|CFStream|NSURLRequest|URLRequest|WKWebView|SFSafariViewController|io\.ktor|okhttp|java\.net\.'
mapfile -t files < <(git ls-files 'core/src/commonMain/*' 'shared/src/commonMain/*' 'shared/src/iosMain/*' 'iosApp/*' | grep -E '\.(kt|swift|m|h)$')
if found="$(grep -nE "$pattern" "${files[@]}")"; then
  echo "::error::Networking code in the shared or iPhone code, which must not reach the internet:"
  echo "$found"
  exit 1
fi
echo "No networking code in ${#files[@]} shared and iPhone source files."
if [ $# -ge 1 ]; then
  binary="$1"
  if linked="$(nm -u "$binary" | grep -E '_OBJC_CLASS_\$_(NSURLSession|NSURLConnection|NSURLRequest|NSMutableURLRequest|WKWebView|SFSafariViewController)|_nw_connection_|_CFSocketCreate|_CFStreamCreatePairWithSocket')"; then
    echo "::error::The iPhone app links networking classes:"
    echo "$linked"
    exit 1
  fi
  echo "The iPhone app links no networking classes."
fi
