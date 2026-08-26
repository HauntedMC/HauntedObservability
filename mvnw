#!/bin/sh
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPS="$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"
[ -f "$PROPS" ] || { echo "Missing $PROPS" >&2; exit 1; }

DIST_URL=$(sed -n 's/^distributionUrl=//p' "$PROPS" | head -n 1)
[ -n "$DIST_URL" ] || { echo "Missing distributionUrl in $PROPS" >&2; exit 1; }
DIST_FILE=${DIST_URL##*/}
DIST_NAME=${DIST_FILE%-bin.zip}
MAVEN_USER_HOME=${MAVEN_USER_HOME:-"$HOME/.m2"}
MAVEN_HOME="$MAVEN_USER_HOME/wrapper/dists/$DIST_NAME"

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  TMP_DIR=$(mktemp -d)
  cleanup() { rm -rf -- "$TMP_DIR"; }
  trap cleanup EXIT HUP INT TERM
  ARCHIVE="$TMP_DIR/$DIST_FILE"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$DIST_URL" -o "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$DIST_URL" -O "$ARCHIVE"
  else
    echo "Maven Wrapper requires curl or wget to download Maven." >&2
    exit 1
  fi
  command -v unzip >/dev/null 2>&1 || { echo "Maven Wrapper requires unzip." >&2; exit 1; }
  unzip -q "$ARCHIVE" -d "$TMP_DIR"
  EXTRACTED="$TMP_DIR/$DIST_NAME"
  [ -x "$EXTRACTED/bin/mvn" ] || { echo "Unexpected Maven archive layout." >&2; exit 1; }
  mkdir -p "$(dirname -- "$MAVEN_HOME")"
  if [ ! -d "$MAVEN_HOME" ]; then
    mv "$EXTRACTED" "$MAVEN_HOME"
  fi
  cleanup
  trap - EXIT HUP INT TERM
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
