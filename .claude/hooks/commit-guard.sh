#!/usr/bin/env bash
# Guards what reaches the pull request branch (AGENTS.md, Workflow).
#
# Runs before every Bash command and acts only on git commit and git push:
#
#   trailers  commit messages may not carry Co-Authored-By or any attribution line
#   push      only a commit that passed a full etc/ci-local.sh check may be pushed
#             (the main session may push after a --quick check)
set -uo pipefail

input="$(cat)"
agent="$(jq -r '.agent_type // empty' <<< "$input")"
command="$(jq -r '.tool_input.command // empty' <<< "$input")"
cwd="$(jq -r '.cwd // empty' <<< "$input")"

deny() {
  jq -n --arg r "$1" '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $r}}'
  exit 0
}

grep -qE 'git[[:space:]].*(commit|push)' <<< "$command" || exit 0

if [[ "$command" =~ (^|[;&|[:space:]])cd[[:space:]]+([^[:space:];&|]+) ]]; then
  dir="${BASH_REMATCH[2]}"
  [[ "$dir" = /* ]] || dir="$cwd/$dir"
elif [[ "$command" =~ git[[:space:]]+-C[[:space:]]+([^[:space:];&|]+) ]]; then
  dir="${BASH_REMATCH[1]}"
  [[ "$dir" = /* ]] || dir="$cwd/$dir"
else
  dir="$cwd"
fi
git -C "$dir" rev-parse --git-dir > /dev/null 2>&1 || exit 0
common="$(cd "$dir" && cd "$(git rev-parse --git-common-dir)" && pwd)"

if grep -qiE 'co-authored-by|generated with \[?claude|noreply@anthropic' <<< "$command"; then
  deny "Commit messages carry no trailer and no attribution of any kind. Remove the Co-Authored-By / Generated-with line."
fi

if grep -qE 'git[[:space:]].*push' <<< "$command"; then
  head="$(git -C "$dir" rev-parse HEAD)"
  if [ -z "$agent" ] && [ -f "$common/parity-green/quick-$head" ]; then
    :
  elif [ ! -f "$common/parity-green/$head" ]; then
    deny "HEAD ${head:0:9} has not passed etc/ci-local.sh check (--quick is enough for the main session). Run it on a clean tree first; only green commits are pushed."
  fi
fi
exit 0
