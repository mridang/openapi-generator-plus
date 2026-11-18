#!/usr/bin/env bash
# Runs inside maven:3.9-eclipse-temurin-21. Mounts: /golden (generated java SDK, read-only), /fixtures, /bench (this folder).
set -euo pipefail
cp -r /golden /work && cd /work
mvn -q -B dependency:copy-dependencies -DoutputDirectory=libs -DincludeScope=runtime >&2
mvn -q -B dependency:copy -Dartifact=com.github.spotbugs:spotbugs-annotations:4.8.6 -DoutputDirectory=libs >&2
for a in blackbird afterburner; do
  mvn -q -B dependency:copy -Dartifact="com.fasterxml.jackson.module:jackson-module-$a:2.19.4" -DoutputDirectory=libs >&2
done
mkdir -p classes
cp /bench/Bench.java src/main/java/com/example/petstore/Bench.java
javac -proc:none -nowarn -d classes -cp "libs/*" $(find src/main/java -name '*.java') >&2
for impl in generated blackbird afterburner handstream; do
  java -Xms1g -Xmx1g -XX:+UseParallelGC -cp "classes:libs/*" com.example.petstore.Bench "$impl"
done
