#!/usr/bin/env bash
# Reads one backup rules file as `aapt2 dump xmltree` prints it, on standard input, and fails
# unless it leaves out everything:
#   rules   data-extraction-rules (Android 12 and later): both <cloud-backup> and
#           <device-transfer> are there, and each excludes every domain whole.
#   legacy  full-backup-content (Android 11 and earlier): every domain is excluded whole.
# A domain counts as excluded only by <exclude domain="..." path=".">, and any <include> fails.
# Usage: check-extraction-rules.sh rules|legacy < xmltree.txt
#        check-extraction-rules.sh --self-test
set -euo pipefail

domains="root file database sharedpref external device_root device_file device_database device_sharedpref"

check() {
  awk -v mode="$1" -v domains="$domains" '
    function flush() {
      if (element == "exclude" && domain != "" && path == ".") excluded[section, domain] = 1
      else if (element == "exclude") { print "an <exclude> in <" section "> does not leave out a whole domain (domain \"" domain "\", path \"" path "\")"; bad = 1 }
      element = ""; domain = ""; path = ""
    }
    /^[[:space:]]*E: / {
      flush()
      name = $2
      if (name == "data-extraction-rules" || name == "full-backup-content") { root = name; if (name == "full-backup-content") section = name; next }
      if (name == "cloud-backup" || name == "device-transfer") { section = name; seen[name] = 1; next }
      if (name == "include") { print "an <include> in <" section "> lets data out"; bad = 1; next }
      if (name == "exclude") { element = "exclude"; next }
      print "unexpected <" name ">"; bad = 1
      next
    }
    /^[[:space:]]*A: / {
      line = $0
      if (match(line, /A: (.*:)?domain(\([^)]*\))?="[^"]*"/)) { value = substr(line, RSTART, RLENGTH); sub(/^[^"]*"/, "", value); sub(/"$/, "", value); domain = value }
      if (match(line, /A: (.*:)?path(\([^)]*\))?="[^"]*"/)) { value = substr(line, RSTART, RLENGTH); sub(/^[^"]*"/, "", value); sub(/"$/, "", value); path = value }
    }
    END {
      flush()
      want = (mode == "rules") ? "data-extraction-rules" : "full-backup-content"
      if (root != want) { print "this is not a <" want "> file (found \"" root "\")"; bad = 1 }
      count = split((mode == "rules") ? "cloud-backup device-transfer" : "full-backup-content", sections, " ")
      total = split(domains, wanted, " ")
      for (s = 1; s <= count; s++) {
        if (mode == "rules" && !seen[sections[s]]) { print "there is no <" sections[s] "> section"; bad = 1; continue }
        for (d = 1; d <= total; d++) {
          if (!excluded[sections[s], wanted[d]]) { print "<" sections[s] "> does not exclude \"" wanted[d] "\""; bad = 1 }
        }
      }
      exit bad ? 1 : 0
    }
  '
}

# What aapt2 prints for a rules file whose sections each list the given domains.
sample_rules() {
  echo "E: data-extraction-rules (line=3)"
  for section in cloud-backup device-transfer; do
    echo "  E: $section (line=4)"
    for domain in "$@"; do
      echo "    E: exclude (line=5)"
      echo "      A: domain=\"$domain\" (Raw: \"$domain\")"
      echo "      A: path=\".\" (Raw: \".\")"
    done
  done
}

self_test() {
  local failed=0
  # Each runs at the end of a pipe, so it reports by its exit status.
  expect() { # expect pass|fail mode description, with the dump on standard input
    local want="$1" mode="$2" what="$3" got=pass
    check "$mode" > /dev/null || got=fail
    if [ "$got" != "$want" ]; then echo "::error::Self-test: $what should $want."; return 1; fi
  }
  # shellcheck disable=SC2086
  sample_rules $domains | expect pass rules "rules that exclude everything" || failed=1
  sample_rules root file database sharedpref external | expect fail rules "rules that keep device-protected storage" || failed=1
  # shellcheck disable=SC2086
  sample_rules $domains | sed '/device-transfer/,$d' | expect fail rules "rules with no device-transfer section" || failed=1
  # shellcheck disable=SC2086
  { sample_rules $domains; echo "    E: include (line=9)"; echo "      A: domain=\"file\" (Raw: \"file\")"; } | expect fail rules "rules with an include" || failed=1
  # shellcheck disable=SC2086
  sample_rules $domains | sed 's/path="\."/path="cache"/' | expect fail rules "rules that exclude only a folder" || failed=1
  echo "E: data-extraction-rules (line=3)" | expect fail rules "empty rules" || failed=1
  # shellcheck disable=SC2086
  sample_rules $domains | expect fail legacy "a rules file given as the old kind" || failed=1
  {
    echo "E: full-backup-content (line=3)"
    for domain in $domains; do
      echo "  E: exclude (line=4)"
      echo "    A: path=\".\" (Raw: \".\")"
      echo "    A: domain=\"$domain\" (Raw: \"$domain\")"
    done
  } | expect pass legacy "old rules that exclude everything" || failed=1
  echo "E: full-backup-content (line=3)" | expect fail legacy "empty old rules" || failed=1
  if [ "$failed" -ne 0 ]; then exit 1; fi
  echo "Extraction-rules check: self-test passed."
}

case "${1:-}" in
  --self-test) self_test ;;
  rules|legacy) check "$1" ;;
  *) echo "Usage: $0 rules|legacy < xmltree.txt, or $0 --self-test" >&2; exit 2 ;;
esac
