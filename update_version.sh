#!/usr/bin/env bash
set -euo pipefail

readonly POM_FILE="pom.xml"
readonly VERSION_PROPERTY="revision"
readonly VERSIONS_PLUGIN="org.codehaus.mojo:versions-maven-plugin:2.18.0"
readonly MODULES=(
  haunted-observability-bom
  haunted-observability-api
  haunted-observability-core
  haunted-observability-testkit
  haunted-observability-featureframework
  haunted-observability-dataprovider
  haunted-observability-dataregistry
  haunted-observability-paper
  haunted-observability-velocity
)

die() { echo "Error: $*" >&2; exit 1; }
usage() {
  cat >&2 <<'USAGE'
Usage: ./update_version.sh [--dry-run] <major|minor|patch>

Bumps HauntedObservability's reactor revision and reproducible-build timestamp,
executes release-equivalent validation, then creates a local release commit and annotated tag.
USAGE
}

resolve_version() {
  local module="${1:-}"
  local -a args=()
  [[ -z "$module" ]] || args=(-pl "$module")
  local version
  version="$(./mvnw -q -ntp "${args[@]}" -DforceStdout help:evaluate -Dexpression=project.version | awk '/^[0-9]+\.[0-9]+\.[0-9]+$/ { print; exit }')"
  [[ -n "$version" ]] || die "Unable to resolve semantic Maven version${module:+ for $module}."
  printf '%s\n' "$version"
}

bump_semver() {
  local current="$1" type="$2"
  [[ "$current" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || die "Current version is not semantic: $current"
  local major="${BASH_REMATCH[1]}" minor="${BASH_REMATCH[2]}" patch="${BASH_REMATCH[3]}"
  case "$type" in
    major) major=$((major + 1)); minor=0; patch=0 ;;
    minor) minor=$((minor + 1)); patch=0 ;;
    patch) patch=$((patch + 1)) ;;
    *) usage; exit 1 ;;
  esac
  printf '%s.%s.%s\n' "$major" "$minor" "$patch"
}

update_timestamp() {
  local timestamp="$1" tmp
  tmp="$(mktemp "${POM_FILE}.XXXXXX")"
  awk -v timestamp="$timestamp" '
    BEGIN { replaced = 0 }
    {
      if (!replaced && $0 ~ /<project\.build\.outputTimestamp>[^<]+<\/project\.build\.outputTimestamp>/) {
        sub(/<project\.build\.outputTimestamp>[^<]+<\/project\.build\.outputTimestamp>/,
            "<project.build.outputTimestamp>" timestamp "</project.build.outputTimestamp>")
        replaced = 1
      }
      print
    }
    END { if (!replaced) exit 2 }
  ' "$POM_FILE" >"$tmp" || { rm -f "$tmp"; die "Could not update project.build.outputTimestamp."; }
  mv "$tmp" "$POM_FILE"
}

DRY_RUN=false
if [[ $# -gt 0 && "$1" == "--dry-run" ]]; then DRY_RUN=true; shift; fi
[[ $# -eq 1 ]] || { usage; exit 1; }
[[ "$1" == "major" || "$1" == "minor" || "$1" == "patch" ]] || { usage; exit 1; }
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "Run this script inside the repository."
cd "$(git rev-parse --show-toplevel)"
[[ -f "$POM_FILE" && -x ./mvnw ]] || die "Missing pom.xml or executable Maven wrapper."

CURRENT="$(resolve_version)"
NEW="$(bump_semver "$CURRENT" "$1")"
TAG="v$NEW"
echo "Current version: $CURRENT"
echo "Bumping to: $NEW"

if [[ "$DRY_RUN" == true ]]; then
  git rev-parse -q --verify "refs/tags/$TAG" >/dev/null 2>&1 && echo "Warning: local tag $TAG already exists." >&2
  echo "Dry run only; no files, commits, or tags were changed."
  exit 0
fi

[[ -z "$(git status --porcelain)" ]] || die "Working tree must be clean."
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null 2>&1 && die "$TAG already exists."

./mvnw -B -ntp "$VERSIONS_PLUGIN:set-property" -Dproperty="$VERSION_PROPERTY" -DnewVersion="$NEW" -DgenerateBackupPoms=false
update_timestamp "$(date -u +%Y-%m-%dT00:00:00Z)"

[[ "$(resolve_version)" == "$NEW" ]] || die "Root reactor version did not update to $NEW."
for module in "${MODULES[@]}"; do
  [[ "$(resolve_version "$module")" == "$NEW" ]] || die "$module did not resolve to $NEW."
done

bash scripts/verify-architecture.sh
./mvnw -U -B -ntp -Prelease install
bash scripts/verify-bom-consumer.sh
git diff --check

git add "$POM_FILE"
git commit -m "Bump version to $TAG for release"
git tag --annotate "$TAG" --message "Release $TAG"
echo "Created release commit and $TAG. Push with: git push origin HEAD && git push origin $TAG"
