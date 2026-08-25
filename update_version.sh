#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage: ./update_version.sh [--dry-run] <major|minor|patch>

Bumps the reactor version, validates the release build, creates a release commit,
and creates an annotated vX.Y.Z tag.

Options:
  --dry-run   Validate the release bump plan without changing files, committing, or tagging.
USAGE
}

DRY_RUN=false
if [[ $# -gt 0 && "$1" == "--dry-run" ]]; then
  DRY_RUN=true
  shift
fi

[[ $# -eq 1 ]] || { usage; exit 1; }
[[ "$1" =~ ^(major|minor|patch)$ ]] || { usage; exit 1; }
BUMP_TYPE="$1"

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

CURRENT="$(mvn -q -ntp -DforceStdout help:evaluate -Dexpression=project.version | tail -n 1)"
[[ "$CURRENT" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || { echo "Current version is not semantic: $CURRENT" >&2; exit 1; }
MAJOR="${BASH_REMATCH[1]}"
MINOR="${BASH_REMATCH[2]}"
PATCH="${BASH_REMATCH[3]}"
case "$BUMP_TYPE" in
  major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
  minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
  patch) PATCH=$((PATCH + 1)) ;;
esac
NEW="$MAJOR.$MINOR.$PATCH"
TAG="v$NEW"

# Keep the release timestamp update guarded: the helper must match exactly one root POM property.
python3 <<'PY'
from pathlib import Path
import re

content = Path("pom.xml").read_text(encoding="utf-8")
pattern = r"<project\.build\.outputTimestamp>[^<]+</project\.build\.outputTimestamp>"
matches = re.findall(pattern, content)
if len(matches) != 1:
    raise SystemExit(f"Expected exactly one project.build.outputTimestamp property, found {len(matches)}.")
PY

echo "Current version: $CURRENT"
echo "Bumping to: $NEW"

if [[ "$DRY_RUN" == true ]]; then
  if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null 2>&1; then
    echo "Warning: local tag $TAG already exists." >&2
  fi
  echo "Dry run only; no files, commits, or tags were changed."
  exit 0
fi

[[ -z "$(git status --porcelain)" ]] || { echo "Working tree must be clean." >&2; exit 1; }
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null 2>&1 && { echo "$TAG already exists" >&2; exit 1; }

mvn -B -ntp org.codehaus.mojo:versions-maven-plugin:2.18.0:set-property \
  -Dproperty=revision -DnewVersion="$NEW" -DgenerateBackupPoms=false
python3 <<'PY'
from datetime import datetime, timezone
from pathlib import Path
import re

path = Path("pom.xml")
content = path.read_text(encoding="utf-8")
timestamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT00:00:00Z")
content, replacements = re.subn(
    r"<project\.build\.outputTimestamp>[^<]+</project\.build\.outputTimestamp>",
    f"<project.build.outputTimestamp>{timestamp}</project.build.outputTimestamp>",
    content,
    count=1,
)
if replacements != 1:
    raise SystemExit(f"Expected to update one project.build.outputTimestamp property, updated {replacements}.")
path.write_text(content, encoding="utf-8")
PY

RESOLVED="$(mvn -q -ntp -DforceStdout help:evaluate -Dexpression=project.version | tail -n 1)"
[[ "$RESOLVED" == "$NEW" ]] || { echo "Resolved version after bump is $RESOLVED, expected $NEW." >&2; exit 1; }

bash scripts/verify-architecture.sh
mvn -U -B -ntp -Prelease install
bash scripts/verify-bom-consumer.sh
git diff --check

git add pom.xml
git commit -m "Bump version to $TAG for release"
git tag --annotate "$TAG" --message "Release $TAG"
echo "Created release commit and $TAG. Push with: git push origin HEAD && git push origin $TAG"
