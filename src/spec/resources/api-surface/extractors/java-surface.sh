#!/bin/sh
# Prints the public API records (D180) of a compiled Java SDK: `javap -protected` over every
# class under the given classes directory, one record per public or protected member, with the
# owning type fully qualified. Types in a package with an `internal` segment are exported all the
# same (the SDK has no module descriptor), so they are reported as internal-but-exported.
set -eu

classes="${1:-target/classes}"
list="$(cd "$classes" && find . -name '*.class' ! -name 'module-info.class' \
  ! -name 'package-info.class' | sed -e 's|^\./||' -e 's|\.class$||' -e 's|/|.|g' | LC_ALL=C sort)"
[ -n "$list" ] || { echo "no classes under $classes" >&2; exit 1; }

# shellcheck disable=SC2086
javap -protected -cp "$classes" $list | awk '
  /^Compiled from / { next }
  /^[^ ].*\{$/ {
    header = $0
    sub(/ *\{$/, "", header)
    owner = ""
    if (header !~ /^public /) { next }
    n = split(header, w, " ")
    for (i = 1; i < n; i++) {
      if (w[i] == "class" || w[i] == "interface" || w[i] == "enum" || w[i] == "record" || w[i] == "@interface") {
        owner = w[i + 1]
        sub(/<.*/, "", owner)
        break
      }
    }
    if (owner == "") { next }
    # Anonymous and local classes are never API, whatever their flags say.
    if (owner ~ /\$[0-9]/) { owner = ""; next }
    print "@@T " owner
    print "@@E " owner
    if (owner ~ /(^|\.)internal\./) { print "@@I " owner }
    print "@@S " header
    next
  }
  /^\}$/ { owner = ""; next }
  /^  / {
    if (owner == "") { next }
    m = $0
    sub(/^ +/, "", m)
    sub(/;$/, "", m)
    if (m == "static {}") { next }
    print "@@S " owner ": " m
  }
'
