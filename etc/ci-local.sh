#!/usr/bin/env bash
# The single definition of "green": CI runs exactly these steps, and a commit or push is
# allowed only after they pass locally on that commit.
#
#   etc/ci-local.sh mvn <args>        run maven with the project JDK (any checkout or worktree)
#   etc/ci-local.sh check [Lang ...]  spotless, spotbugs, unit tests, the integration specs
#                                     (all, or only the given spec prefixes such as Go Ruby),
#                                     and a clean tree after regeneration; records a green
#                                     marker for HEAD on success (full run, clean tree only)
#   etc/ci-local.sh check --quick     the same without the integration specs (each language
#                                     agent already ran its own); used per change set
#   etc/ci-local.sh green [sha]       exit 0 if the commit (default HEAD) passed a full check
#   etc/ci-local.sh approve           record the parity-guard's approval of the staged tree
#                                     (the commit guard lets only that agent run it)
#
# Output of each step is saved under ${TMPDIR:-/tmp}/ci-local-<sha>/ and only a summary is
# printed, so callers never pipe or tail maven output.
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
marks="$(git rev-parse --git-common-dir)/parity-green"

resolve_maven() {
  if command -v mvn > /dev/null 2>&1 && java -version 2>&1 | grep -q '"25'; then
    m=mvn
    return
  fi
  local jdk="" mvn="" d
  for d in /nix/store/*-zulu-ca-jdk-25*/Library/Java/JavaVirtualMachines/*/Contents/Home /nix/store/*-zulu-ca-jdk-25*/lib/openjdk; do
    [ -x "$d/bin/java" ] && jdk="$d"
  done
  for d in /nix/store/*-maven-3.9.*/bin/mvn; do
    [ -x "$d" ] && mvn="$d"
  done
  if [ -z "$jdk" ] || [ -z "$mvn" ]; then
    echo "ci-local: no JDK 25 and maven found (PATH or /nix/store)" >&2
    exit 2
  fi
  export JAVA_HOME="$jdk"
  export PATH="$jdk/bin:$PATH"
  m="$mvn"
}

run_step() {
  local name="$1"
  shift
  if "$@" > "$out/$name.txt" 2>&1; then
    echo "ok    $name"
  else
    echo "FAIL  $name  (see $out/$name.txt)"
    grep -aE '<<< FAIL|ERROR\]|Tests run:.*(Fail|Err)[a-z]*: [1-9]' "$out/$name.txt" | head -20 || true
    exit 1
  fi
}

cmd="${1:-check}"
shift || true
case "$cmd" in
  mvn)
    resolve_maven
    cd "$root" && exec "$m" "$@"
    ;;
  green)
    sha="$(git rev-parse "${1:-HEAD}")"
    [ -f "$marks/$sha" ]
    ;;
  approve)
    mkdir -p "$marks"
    tree="$(git write-tree)"
    touch "$marks/approved-$tree"
    echo "approved ${tree:0:9}"
    ;;
  check)
    resolve_maven
    cd "$root"
    sha="$(git rev-parse HEAD)"
    out="${TMPDIR:-/tmp}/ci-local-${sha:0:9}"
    mkdir -p "$out"
    dirty_before="$(git status --porcelain)"
    quick=0
    if [ "${1:-}" = "--quick" ]; then
      quick=1
      shift
    fi
    run_step spotless "$m" -B spotless:check
    run_step spotbugs "$m" -B compile spotbugs:check
    if [ "$quick" -eq 1 ]; then
      run_step test "$m" -B test
    elif [ "$#" -eq 0 ]; then
      run_step verify "$m" -B verify
    else
      specs="$(printf '%s*Spec,' "$@")"
      run_step verify "$m" -B verify "-Dit.test=${specs%,}" -DfailIfNoTests=false
    fi
    # go.mod and go.sum follow upstream module releases, so they drift without any change
    # here; everything else regenerated must match what is committed.
    generated=(src/spec/resources/generated generated-code
      ':(exclude)src/spec/resources/generated/go/go.mod' ':(exclude)src/spec/resources/generated/go/go.sum')
    if [ -z "$dirty_before" ] && [ -n "$(git status --porcelain -- "${generated[@]}")" ]; then
      echo "FAIL  regenerate  the committed generated code differs from a fresh regeneration:"
      git status --porcelain -- "${generated[@]}" | head -20
      exit 1
    fi
    git checkout -q -- src/spec/resources/generated/go/go.mod src/spec/resources/generated/go/go.sum
    echo "ok    regenerate"
    if [ "$quick" -eq 0 ] && [ "$#" -eq 0 ] && [ -z "$dirty_before" ]; then
      mkdir -p "$marks"
      touch "$marks/$sha"
      echo "green ${sha:0:9}"
    fi
    ;;
  *)
    echo "usage: etc/ci-local.sh mvn <args> | check [Lang ...] | green [sha] | approve" >&2
    exit 2
    ;;
esac
