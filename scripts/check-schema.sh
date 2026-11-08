#!/usr/bin/env bash
# The database layout of every version is kept in app/schemas, so a later version can move the
# user's data forward instead of losing it. Fails if the build produced a layout that is not
# committed, and prints it so it can be added.
set -euo pipefail
changes="$(git status --porcelain --untracked-files=all -- app/schemas)"
if [ -z "$changes" ]; then
  echo "Database layout check passed."
  exit 0
fi
echo "::error::The database layout in app/schemas is not committed. Commit these files:"
printf '%s\n' "$changes"
printf '%s\n' "$changes" | awk '{print $NF}' | while read -r file; do
  echo "----- $file"
  cat "$file"
done
exit 1
