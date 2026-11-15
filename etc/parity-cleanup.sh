#!/usr/bin/env bash
# Frees what parity work leaves behind: agent worktrees that are no longer in use (no
# runtime container of theirs is running and nothing in them changed for 20 minutes; their
# branches are kept), stopped mock-server and proxy containers, and leaked containers and
# test networks (through the Docker guard's sweep).
#
#   etc/parity-cleanup.sh
set -uo pipefail

repo="$(cd "$(git rev-parse --git-common-dir)/.." && pwd)"
cd "$repo"

active="$(docker ps --format '{{.Label "com.mridang.openapi.workdir"}}' 2> /dev/null | sort -u)"
removed=0
kept=0
while read -r wt; do
  case "$wt" in */.claude/worktrees/wf_*) ;; *) continue ;; esac
  if grep -qx "$wt" <<< "$active" || [ -n "$(find "$wt" -maxdepth 4 -newermt '-20 minutes' -print -quit 2> /dev/null)" ]; then
    kept=$((kept + 1))
    continue
  fi
  git worktree remove --force "$wt" && removed=$((removed + 1))
done < <(git worktree list --porcelain | awk '/^worktree /{print $2}')
git worktree prune

for image in mridang/chasm:1.3.0 ubuntu/squid:6.10-24.10_beta; do
  docker ps -aq --filter status=exited --filter "ancestor=$image" 2> /dev/null | xargs -r docker rm > /dev/null 2>&1
done
echo '{"hook_event_name":"SessionStart"}' | "$repo/.claude/hooks/docker-guard.sh"

echo "worktrees removed $removed, kept $kept; containers running $(docker ps -q 2> /dev/null | wc -l | tr -d ' ')"
