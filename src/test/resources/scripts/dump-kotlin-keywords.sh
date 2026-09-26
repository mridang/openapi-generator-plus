#!/bin/sh
# Run Kotlin script to dump keywords. Uses kotlinc if available,
# otherwise extracts quoted strings from the keywords list.
if command -v kotlinc >/dev/null 2>&1; then
    kotlinc -script /scripts/kotlin_keywords.kts 2>/dev/null
elif command -v kotlin >/dev/null 2>&1; then
    kotlin /scripts/kotlin_keywords.kts 2>/dev/null
else
    # Fallback: parse the keyword strings from the Kotlin source
    sed -n 's/.*"\([a-z]*\)".*/\1/p' /scripts/kotlin_keywords.kts | sort -u
fi
