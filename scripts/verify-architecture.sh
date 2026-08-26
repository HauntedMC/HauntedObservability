#!/usr/bin/env bash
set -euo pipefail

fail_if_present() {
  local pattern="$1"
  shift
  if grep -R -n -E --include='*.java' "$pattern" "$@"; then
    echo "Architecture boundary violation: $pattern" >&2
    exit 1
  fi
}

API_SRC="haunted-observability-api/src/main/java"
CORE_SRC="haunted-observability-core/src/main/java"
FF_SRC="haunted-observability-featureframework/src/main/java"
DP_SRC="haunted-observability-dataprovider/src/main/java"
DR_SRC="haunted-observability-dataregistry/src/main/java"
PAPER_SRC="haunted-observability-paper/src/main/java"
VELOCITY_SRC="haunted-observability-velocity/src/main/java"

fail_if_present 'io\.opentelemetry|org\.bukkit|com\.velocitypowered' "$API_SRC"
fail_if_present 'org\.bukkit|com\.velocitypowered|nl\.hauntedmc\.featureframework|nl\.hauntedmc\.dataprovider|nl\.hauntedmc\.dataregistry' "$CORE_SRC"
fail_if_present 'nl\.hauntedmc\.dataprovider|nl\.hauntedmc\.dataregistry' "$FF_SRC"
fail_if_present 'nl\.hauntedmc\.featureframework|nl\.hauntedmc\.dataregistry' "$DP_SRC"
fail_if_present 'nl\.hauntedmc\.featureframework|nl\.hauntedmc\.dataprovider' "$DR_SRC"
fail_if_present 'com\.velocitypowered' "$PAPER_SRC"
fail_if_present 'org\.bukkit' "$VELOCITY_SRC"

# Operation telemetry must never encode known sensitive/high-cardinality values.
fail_if_present 'playerUuid|playerUUID|username|ipAddress|sqlText|queryText|redisKey|payload|connectionIdentifier' \
  "$API_SRC" "$CORE_SRC" "$FF_SRC" "$DP_SRC" "$DR_SRC"

echo "HauntedObservability architecture boundaries verified."
