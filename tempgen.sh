#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_DIR="/Users/mridang/Junk/sdk/config"
SPEC_DIR="/Users/mridang/Junk/sdk/spec"
OUTPUT_DIR="/tmp/fogenit"
CLI_JAR="${SCRIPT_DIR}/target/openapi-generator-cli-7.14.0.jar"

NORMALIZER_ARGS="NORMALIZER_CLASS=io.github.mridang.codegen.AdvancedOpenAPINormalizer,GARBAGE_COLLECT_COMPONENTS=1,STRIP_PARAMS=Connect-Protocol-Version|Connect-Timeout-Ms,CLEAN_EMPTY_REQUEST_BODIES=Tag,ONLY_ALLOW_JSON=1"

LANGUAGES=(java python ruby php node csharp)

JVM_OPTS=(-Xms512m -Xmx1500m)

# Download the CLI jar if missing
if [[ ! -f "${CLI_JAR}" ]]; then
  echo "==> Downloading openapi-generator-cli-7.14.0.jar..."
  curl -sL -o "${CLI_JAR}" \
    "https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/7.14.0/openapi-generator-cli-7.14.0.jar"
fi

# Build the project
echo "==> Building codegen-plus..."
(cd "${SCRIPT_DIR}" && devbox run -- mvn package -DskipTests -q)

CLASSPATH="${CLI_JAR}:${SCRIPT_DIR}/target/codegen-plus-1.9.2.jar:${SCRIPT_DIR}/target/lib/*"

echo "==> Clearing ${OUTPUT_DIR}..."
rm -rf "${OUTPUT_DIR}"
mkdir -p "${OUTPUT_DIR}"

for lang in "${LANGUAGES[@]}"; do
  echo "==> Generating ${lang} client..."
  mkdir -p "${OUTPUT_DIR}/${lang}"

  java "${JVM_OPTS[@]}" \
    -cp "${CLASSPATH}" \
    org.openapitools.codegen.OpenAPIGenerator generate \
      --input-spec="${SPEC_DIR}/client/v4.11.0/index.json" \
      --generator-name="${lang}-plus" \
      --output="${OUTPUT_DIR}/${lang}" \
      --git-user-id=zitadel \
      --git-repo-id=sdk \
      --git-host=github.com \
      --config="${CONFIG_DIR}/${lang}/proc.yml" \
      --openapi-normalizer \
      "${NORMALIZER_ARGS}"

  echo "==> Done: ${lang}"
done

echo "==> All clients generated in ${OUTPUT_DIR}/"
