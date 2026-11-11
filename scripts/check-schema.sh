#!/usr/bin/env bash
# The database layout of every version is kept in shared/schemas, so a later version can move the
# user's data forward instead of losing it. Fails if the build produced a layout that is not
# committed, or one that differs from the committed file, and prints it so it can be added.
# Formatting differences alone (spacing, a final newline) don't count. On a branch it only warns,
# so a version that changes the database doesn't fail its first run; main always fails, so
# nothing is released without its layout.
set -euo pipefail
status="$(git status --porcelain --untracked-files=all -- shared/schemas)"
level=warning
if [ "${GITHUB_REF:-}" = "refs/heads/main" ]; then level=error; fi
failed=0
while read -r code file; do
  [ -z "${file:-}" ] && continue
  if [ "$code" = "M" ] && python3 - "$file" <<'PY'
import json, subprocess, sys
path = sys.argv[1]
committed = json.loads(subprocess.run(["git", "show", "HEAD:" + path], check=True, capture_output=True, text=True).stdout)
built = json.load(open(path))
sys.exit(0 if committed == built else 1)
PY
  then
    continue
  fi
  echo "::$level::The database layout $file is not committed, or differs from the committed one. Commit it:"
  echo "----- $file"
  cat "$file"
  echo
  echo "----- end of $file"
  failed=1
done <<< "$status"
if [ "$failed" != 0 ]; then
  if [ "$level" = error ]; then exit 1; fi
  echo "Not failing on a branch: commit the layout above before this goes to main."
  exit 0
fi
echo "Database layout check passed."
