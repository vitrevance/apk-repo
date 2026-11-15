#!/usr/bin/env bash
# Build the project's Docker image.
#
# Usage:
#   ./build.sh <image-name> [additional docker build args...]
#
# Example:
#   ./build.sh apk-repo:latest
#   ./build.sh apk-repo:test --no-cache
#
# The Dockerfile lives at the repository root alongside this script, and its
# build context is the repository root as well, because it copies backend/
# and web/ (Kotlin/Wasm frontend) into the image.

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <image-name> [additional docker build args...]" >&2
    exit 1
fi

IMAGE_NAME="$1"
shift

# Resolve the repository root from this script's location, so the script
# works no matter which directory it is invoked from.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec docker build \
    -f "$REPO_ROOT/Dockerfile" \
    -t "$IMAGE_NAME" \
    "$@" \
    "$REPO_ROOT"
