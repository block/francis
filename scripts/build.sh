#!/bin/bash
set -euxo pipefail

root_dir="$(realpath "$(dirname "$(dirname "$(readlink -f "$0")")")")"
cd "$root_dir"

GRAALVM_HOME="$(brew info graalvm-ce-java17 | grep 'export JAVA_HOME' | sed -E 's/^ *export JAVA_HOME="(.*)"/\1/')"
export GRAALVM_HOME

./gradlew :main:nativeCompile
