#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

./gradlew :hostSdk:test "$@"
