#!/bin/sh
# Prints the public API records (D180) of the generated Kotlin SDK. The Kotlin Gradle plugin's own
# ABI validation writes the JVM ABI dump (the binary-compatibility-validator format), which already
# leaves out `internal` and private declarations by reading the Kotlin metadata. Synthetic classes
# (the serialization plugin's `$$serializer`) and synthetic or `$`-mangled members (`copy$default`,
# `write$Self`) are compiler artefacts, not API, and are dropped. Runs from the SDK root, on a copy.
set -eu

cat >> build.gradle.kts << 'EOF'

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }
}
EOF

gradle dumpLegacyAbi -q > /dev/null
dump="$(find build/kotlin/abi-legacy -name '*.api' | head -1)"
[ -n "$dump" ] || { echo "no ABI dump under build/kotlin/abi-legacy" >&2; exit 1; }

awk '
  /^[a-z].*\{$/ {
    header = $0
    sub(/ *\{$/, "", header)
    owner = ""
    if (header ~ / synthetic /) { next }
    rest = header
    sub(/^.* class /, "", rest)
    sub(/ .*$/, "", rest)
    owner = rest
    print "@@T " owner
    print "@@E " owner
    if (owner ~ /(^|\/)internal\//) { print "@@I " owner }
    print "@@S " header
    next
  }
  /^\}$/ { owner = ""; next }
  /^\t/ {
    if (owner == "") { next }
    m = $0
    sub(/^\t+/, "", m)
    if (m ~ /(^| )synthetic /) { next }
    n = split(m, w, " ")
    for (i = 1; i < n; i++) {
      if ((w[i] == "fun" || w[i] == "field") && w[i + 1] ~ /\$/) { next }
    }
    print "@@S " owner ": " m
  }
' "$dump"
