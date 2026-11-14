#!/usr/bin/env bash
# Guards what reaches the pull request branch (AGENTS.md, Parity workflow).
#
# Runs before every Bash command and acts only on git commit, git push and the approval
# command:
#
#   halt      while <git-common-dir>/parity-halt exists (CI failed after a local pass),
#             no commit on the PR branch and no push is allowed until the gap in
#             etc/ci-local.sh is found and the file is removed
#   trailers  commit messages may not carry Co-Authored-By or any attribution line
#   approval  a commit on the PR branch (or a parity/integrate-* branch that lands on it)
#             that changes src/ needs the parity-guard's
#             approval of exactly the staged tree (etc/ci-local.sh approve); only the
#             parity-guard agent may run that command
#   push      only a commit that passed a full etc/ci-local.sh check may be pushed
set -uo pipefail

PR_BRANCH="feat/better-generated-clients"

input="$(cat)"
agent="$(jq -r '.agent_type // empty' <<< "$input")"
command="$(jq -r '.tool_input.command // empty' <<< "$input")"
cwd="$(jq -r '.cwd // empty' <<< "$input")"

deny() {
  jq -n --arg r "$1" '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $r}}'
  exit 0
}

grep -qE 'git[[:space:]].*(commit|push)|ci-local\.sh[[:space:]]+approve' <<< "$command" || exit 0

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
branch="$(git -C "$dir" symbolic-ref --short -q HEAD)"

if grep -qE 'ci-local\.sh[[:space:]]+approve' <<< "$command" && [ "$agent" != "parity-guard" ]; then
  deny "Only the parity-guard agent may approve a change set (it reviews all 12 SDKs first)."
fi

if grep -qiE 'co-authored-by|generated with \[?claude|noreply@anthropic' <<< "$command"; then
  deny "Commit messages carry no trailer and no attribution of any kind. Remove the Co-Authored-By / Generated-with line."
fi

if grep -qE 'git[[:space:]].*commit' <<< "$command" && { [ "$branch" = "$PR_BRANCH" ] || [[ "$branch" == parity/integrate-* ]]; }; then
  if [ -f "$common/parity-halt" ]; then
    deny "Halted: $(cat "$common/parity-halt"). Find what the local run missed, add it to etc/ci-local.sh, then remove $common/parity-halt."
  fi
  if [ -n "$(git -C "$dir" diff --cached --name-only -- src)" ] || [ -f "$(git -C "$dir" rev-parse --git-dir)/MERGE_HEAD" ]; then
    tree="$(git -C "$dir" write-tree 2> /dev/null)"
    if [ ! -f "$common/parity-green/approved-$tree" ]; then
      deny "This commit changes SDK code on $PR_BRANCH without the parity-guard's approval of this exact staged tree. Run the parity-guard review (it approves with etc/ci-local.sh approve), or use /parity-fix."
    fi
  fi
fi

if grep -qE 'git[[:space:]].*push' <<< "$command"; then
  if [ -f "$common/parity-halt" ]; then
    deny "Halted: $(cat "$common/parity-halt"). No pushes until the local check catches what CI caught."
  fi
  head="$(git -C "$dir" rev-parse HEAD)"
  if [ ! -f "$common/parity-green/$head" ]; then
    deny "HEAD ${head:0:9} has not passed a full etc/ci-local.sh check. Run it on a clean tree first; only green commits are pushed."
  fi
fi
exit 0
