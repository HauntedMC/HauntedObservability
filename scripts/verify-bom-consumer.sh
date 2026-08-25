#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
VERSION="$(mvn -q -ntp -DforceStdout help:evaluate -Dexpression=project.version | tail -n 1)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/src/main/java/example"

cat > "$WORK/pom.xml" <<POM
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>observability-consumer</artifactId>
  <version>1.0.0</version>
  <properties><maven.compiler.release>25</maven.compiler.release></properties>
  <dependencyManagement><dependencies><dependency>
    <groupId>nl.hauntedmc.observability</groupId>
    <artifactId>haunted-observability-bom</artifactId>
    <version>${VERSION}</version>
    <type>pom</type>
    <scope>import</scope>
  </dependency></dependencies></dependencyManagement>
  <dependencies>
    <dependency><groupId>nl.hauntedmc.observability</groupId><artifactId>haunted-observability-api</artifactId></dependency>
    <dependency><groupId>nl.hauntedmc.observability</groupId><artifactId>haunted-observability-core</artifactId></dependency>
    <dependency><groupId>nl.hauntedmc.observability</groupId><artifactId>haunted-observability-featureframework</artifactId></dependency>
    <dependency><groupId>nl.hauntedmc.observability</groupId><artifactId>haunted-observability-dataprovider</artifactId></dependency>
    <dependency><groupId>nl.hauntedmc.observability</groupId><artifactId>haunted-observability-dataregistry</artifactId></dependency>
    <dependency><groupId>nl.hauntedmc.observability</groupId><artifactId>haunted-observability-paper</artifactId></dependency>
    <dependency><groupId>nl.hauntedmc.observability</groupId><artifactId>haunted-observability-velocity</artifactId></dependency>
    <dependency><groupId>nl.hauntedmc.observability</groupId><artifactId>haunted-observability-testkit</artifactId></dependency>
  </dependencies>
</project>
POM

cat > "$WORK/src/main/java/example/Consumer.java" <<'JAVA'
package example;

import nl.hauntedmc.observability.api.ObservabilityConfig;

public final class Consumer {
    private final ObservabilityConfig config = ObservabilityConfig.defaults();
}
JAVA

mvn -B -ntp -f "$WORK/pom.xml" -DskipTests package
