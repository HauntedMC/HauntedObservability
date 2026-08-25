#!/usr/bin/env bash
set -euo pipefail
usage() { echo "Usage: ./update_version.sh <major|minor|patch>" >&2; }
[[ $# -eq 1 ]] || { usage; exit 1; }
[[ "$1" =~ ^(major|minor|patch)$ ]] || { usage; exit 1; }
ROOT="$(git rev-parse --show-toplevel)"; cd "$ROOT"
[[ -z "$(git status --porcelain)" ]] || { echo "Working tree must be clean." >&2; exit 1; }
CURRENT="$(mvn -q -ntp -DforceStdout help:evaluate -Dexpression=project.version | tail -n 1)"
[[ "$CURRENT" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || { echo "Current version is not semantic: $CURRENT" >&2; exit 1; }
MAJOR="${BASH_REMATCH[1]}"; MINOR="${BASH_REMATCH[2]}"; PATCH="${BASH_REMATCH[3]}"
case "$1" in
  major) MAJOR=$((MAJOR+1)); MINOR=0; PATCH=0 ;;
  minor) MINOR=$((MINOR+1)); PATCH=0 ;;
  patch) PATCH=$((PATCH+1)) ;;
esac
NEW="$MAJOR.$MINOR.$PATCH"; TAG="v$NEW"
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && { echo "$TAG already exists" >&2; exit 1; }
mvn -B -ntp org.codehaus.mojo:versions-maven-plugin:2.18.0:set-property -Dproperty=revision -DnewVersion="$NEW" -DgenerateBackupPoms=false
python3 - "$NEW" <<'PY'
from pathlib import Path
from datetime import datetime, timezone
p=Path('pom.xml'); s=p.read_text();
import re
s=re.sub(r'<project\.build\.outputTimestamp>[^<]+</project\.build.outputTimestamp>', '<project.build.outputTimestamp>'+datetime.now(timezone.utc).strftime('%Y-%m-%dT00:00:00Z')+'</project.build.outputTimestamp>', s, count=1)
p.write_text(s)
PY
mvn -U -B -ntp -Prelease verify
git diff --check
git add pom.xml
git commit -m "Bump version to $TAG for release"
git tag --annotate "$TAG" --message "Release $TAG"
echo "Created release commit and $TAG. Push with: git push origin HEAD && git push origin $TAG"
