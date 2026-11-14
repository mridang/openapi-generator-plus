#!/usr/bin/env bash
# Enforces "one language, one agent" (AGENTS.md, Parity workflow).
#
# Runs before Edit, Write, MultiEdit, NotebookEdit and Bash. Every file belongs to exactly
# one owner, and only that owner may write it:
#
#   sdk-<lang>     src/main/resources/templates/<lang>/, src/{main,test}/java/**/generators/<lang>/,
#                  src/spec/java/**/spec/<lang>/, src/spec/resources/generated/<lang>/
#   sdk-shared     the rest of src/ (shared codegen, spec classes, the petstore spec,
#                  reserved words) and pom.xml; it may also regenerate every golden
#   coordinator    everything else (.claude/, etc/, AGENTS.md, the ledger): the main
#                  session and the parity-integrator agent, never a language agent
#
# The main session has no agent_type and owns no SDK code, so it cannot hand-edit a
# language. For Bash only obvious writes are checked (sed -i, perl -i, tee, cp, mv, rm,
# patch, git apply, redirection); maven regeneration and git operations are allowed, and
# the parity-guard review catches anything this misses.
set -uo pipefail

input="$(cat)"
tool="$(jq -r '.tool_name // empty' <<< "$input")"
agent="$(jq -r '.agent_type // empty' <<< "$input")"

LANGS='java|kotlin|csharp|php|python|ruby|node|dart|go|rust|swift|elixir'

owner_of() {
  local p="$1"
  if [[ "$p" =~ src/main/resources/templates/($LANGS)(/|$) ]] ||
    [[ "$p" =~ src/(main|test)/java/.*/generators/($LANGS)(/|$) ]] ||
    [[ "$p" =~ src/spec/java/.*/spec/($LANGS)(/|$) ]] ||
    [[ "$p" =~ src/spec/resources/generated/($LANGS)(/|$) ]]; then
    echo "sdk-${BASH_REMATCH[${#BASH_REMATCH[@]}-2]}"
  elif [[ "$p" =~ (^|/)src/ ]] || [[ "$p" =~ (^|/)pom\.xml$ ]]; then
    echo "sdk-shared"
  else
    echo "coordinator"
  fi
}

allowed() {
  local owner="$1"
  case "$owner" in
    coordinator) [ -z "$agent" ] || [ "$agent" = "parity-integrator" ] ;;
    sdk-shared) [ "$agent" = "sdk-shared" ] ;;
    *) [ "$agent" = "$owner" ] ;;
  esac
}

deny() {
  jq -n --arg r "$1" '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $r}}'
  exit 0
}

check_path() {
  local p="$1" owner
  owner="$(owner_of "$p")"
  if [ "$owner" = "sdk-shared" ] && [ "$agent" = "sdk-shared" ]; then
    return
  fi
  if [[ "$p" =~ src/spec/resources/generated/ ]] && [ "$agent" = "sdk-shared" ]; then
    return
  fi
  allowed "$owner" && return
  deny "$p belongs to $owner, and this is ${agent:-the main session}. Only $owner may change it: hand the change to the $owner agent (one language, one agent; see AGENTS.md, Parity workflow)."
}

case "$tool" in
  Edit | Write | MultiEdit | NotebookEdit)
    path="$(jq -r '.tool_input.file_path // .tool_input.notebook_path // empty' <<< "$input")"
    [ -n "$path" ] && check_path "$path"
    ;;
  Bash)
    command="$(jq -r '.tool_input.command // empty' <<< "$input")"
    grep -qE 'src/|pom\.xml' <<< "$command" || exit 0
    targets=""
    if grep -qE '(^|[;&|[:space:]])(sed[[:space:]]+(-[a-zA-Z]*[[:space:]]+)*-i|perl[[:space:]]+-[a-zA-Z]*i|tee|cp|mv|rm|patch|install|rsync)([[:space:]]|$)|git[[:space:]]+apply' <<< "$command"; then
      targets="$(grep -oE '[^[:space:]"'"'"']*(src/[^[:space:]"'"'"';|&)]+|pom\.xml)' <<< "$command")"
    fi
    targets="$targets
$(grep -oE '>>?[[:space:]]*[^[:space:]"'"'"';|&)]*(src/[^[:space:]"'"'"';|&)]+|pom\.xml)' <<< "$command" | sed -E 's/^>>?[[:space:]]*//')"
    while IFS= read -r t; do
      [ -n "$t" ] && check_path "$t"
    done <<< "$targets"
    ;;
esac
exit 0
