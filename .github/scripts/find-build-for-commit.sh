#!/bin/bash
set -euo pipefail

COMMIT_SHA="$1"

# Find the most recent successful build workflow run for this commit
BUILD_RUN_ID=$(gh run list \
  --workflow=build.yaml \
  --commit="$COMMIT_SHA" \
  --status=success \
  --json databaseId \
  --jq '.[0].databaseId')

if [ -z "$BUILD_RUN_ID" ] || [ "$BUILD_RUN_ID" = "null" ]; then
  echo "No successful build found for commit $COMMIT_SHA" >&2
  exit 1
fi

echo "$BUILD_RUN_ID"
