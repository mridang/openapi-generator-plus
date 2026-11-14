---
name: parity-status
description: Say where the SDK parity work stands - the progress number, what landed recently, what is running and anything parked or halted. Use whenever the user asks for status.
---
# /parity-status

Answer in at most five short lines, from these sources only:

1. Progress number: `python3 etc/parity-findings.py count` in the PR worktree (`.claude/worktrees/pr35`), and the open split by severity (`python3 etc/parity-findings.py next 500 | grep -c '\[high\]'` etc.).
2. Landed: `git log --oneline origin/master..HEAD --since='24 hours ago' | wc -l` and the last three subjects.
3. Running: whether a parity-fix workflow is in progress (and which change set), or idle.
4. Problems: parked items (`grep -c '"parked"' PARITY_FINDINGS.json`), and whether `<git-common-dir>/parity-halt` exists (say its reason).
5. CI on the PR: the last run's per-job conclusions (`gh pr checks 35 --repo mridang/openapi-generator-plus`).
