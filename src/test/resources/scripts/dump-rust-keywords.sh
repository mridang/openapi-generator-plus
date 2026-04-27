#!/bin/sh
# Compile and run the Rust keywords source to dump all keywords.
# Falls back to parsing the source file if rustc is not available.
if command -v rustc >/dev/null 2>&1; then
    rustc /scripts/rust_keywords.rs -o /tmp/rust_keywords 2>/dev/null && /tmp/rust_keywords
else
    # Fallback: parse the keyword strings from the Rust source
    sed -n 's/.*"\([a-zA-Z_]*\)".*/\1/p' /scripts/rust_keywords.rs | sort -u
fi
