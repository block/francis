#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

tmp_files=$(mktemp)
cleanup() {
  rm -f "$tmp_files"
}
trap cleanup EXIT

append_if_kotlin_file() {
  local path="$1"
  case "$path" in
    *.kt|*.kts)
      if [ -f "$path" ]; then
        printf '%s\n' "$path" >> "$tmp_files"
      fi
      ;;
  esac
}

collect_range_files() {
  local from_ref="$1"
  local to_ref="$2"

  git diff --name-only --diff-filter=ACMR "$from_ref" "$to_ref" |
    while IFS= read -r path; do
      [ -n "$path" ] || continue
      append_if_kotlin_file "$path"
    done
}

collect_new_ref_files() {
  local local_oid="$1"

  git rev-list "$local_oid" --not --remotes |
    while IFS= read -r commit; do
      [ -n "$commit" ] || continue
      git diff-tree --root --no-commit-id --name-only -r --diff-filter=ACMR "$commit"
    done |
    while IFS= read -r path; do
      [ -n "$path" ] || continue
      append_if_kotlin_file "$path"
    done
}

if [ "${1:-}" = "--files" ]; then
  # Manual testing helper: bypass hook stdin and check an explicit file list.
  shift
  for path in "$@"; do
    append_if_kotlin_file "$path"
  done
else
  # When invoked by `.hooks/pre-push`, Git does not provide a changed-file list.
  # Instead, the hook receives remote metadata as arguments plus stdin lines of
  # the form:
  #   <local-ref> SP <local-object-name> SP <remote-ref> SP <remote-object-name> LF
  # See: https://git-scm.com/docs/githooks#_pre_push
  #
  # We translate those ref updates into the set of Kotlin files introduced by the
  # push so we can run ktfmt only on what is actually being pushed.
  while IFS=' ' read -r local_ref local_oid remote_ref remote_oid; do
    [ -n "${local_oid:-}" ] || continue

    if [[ "$local_oid" =~ ^0+$ ]]; then
      continue
    fi

    if [[ "$remote_oid" =~ ^0+$ ]]; then
      collect_new_ref_files "$local_oid"
    else
      collect_range_files "$remote_oid" "$local_oid"
    fi
  done
fi

include_only=$(sort -u "$tmp_files" | paste -sd, -)

if [ -z "$include_only" ]; then
  echo "No Kotlin files in push; skipping ktfmt check."
  exit 0
fi

echo "Running ktfmtPushCheck on pushed Kotlin files..."
./gradlew --quiet ktfmtPushCheck --include-only="$include_only"
