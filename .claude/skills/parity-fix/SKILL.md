---
name: parity-fix
description: Fix open items from the parity ledger as harmonised change sets across all twelve SDKs (plan, pre-flight, one agent per language, parity-guard approval, green granular commits). Use for any SDK change in this repository, whatever started it.
argument-hint: "[F016,F033 | next N | <area>] [+budget]"
---
# /parity-fix

Every change to SDK code in this repository goes through this command. The main session coordinates only; hooks refuse its edits to SDK code.

1. Sync: in the PR worktree (`.claude/worktrees/pr35`, branch `feat/better-generated-clients`; create it with `git worktree add` if missing) run `git fetch origin`, merge `origin/feat/better-generated-clients` and `origin/master` if they moved, and reinstall nothing else (maven resolves dependencies).
2. Pick the items: the ids given, or `python3 etc/parity-findings.py next <N>` (default 30). Make change sets BIG: each set costs every touched language a full spec run plus a planner and a guard pass, so put every item that touches the same area or files into one set (3 to 8 items; `slug`: a short kebab-case name of the area, e.g. `datetime-offset`). API items (`API surface: harmonise ...`) go together by category and class family (all auth authenticators, all model classes). Machine load, not agent count, is the limit: keep `width` at 4 unless `uptime` shows spare cores.
3. Run the saved workflow by path (a session caches workflows by name from when it started): `Workflow({scriptPath: "<PR worktree>/.claude/workflows/parity-fix.js", args: {pr: <absolute PR worktree path>, base: <git rev-parse HEAD there>, sets: [...]}})`. If the user gave a token budget (`+2M`), pass it on in the same message so the workflow stops cleanly.
4. When it finishes, push only if `etc/ci-local.sh green` passes on HEAD: run `etc/ci-local.sh check > /tmp/ci-full.txt 2>&1` in the background, then `git push origin feat/better-generated-clients`. The commit guard refuses anything else.
5. Report ONE line per change set (landed / parked / false-positive) and the progress number `python3 etc/parity-findings.py count`.

If CI fails on a commit that passed locally: write the failing job and reason into `<git-common-dir>/parity-halt` (this stops all commits and pushes), find what the local run missed, add that check to `etc/ci-local.sh`, fix, and only then delete the halt file.
