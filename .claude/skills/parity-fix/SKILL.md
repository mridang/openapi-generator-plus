---
name: parity-fix
description: Fix open items from the parity ledger as harmonised change sets across all twelve SDKs (plan, pre-flight, one agent per language, parity-guard approval, green granular commits). Use for any SDK change in this repository, whatever started it.
argument-hint: "[F016,F033 | next N | <area>] [+budget]"
---
# /parity-fix

Every change to SDK code in this repository goes through this command. The main session coordinates only; hooks refuse its edits to SDK code.

1. Sync: in the PR worktree (`.claude/worktrees/pr35`, branch `feat/better-generated-clients`; create it with `git worktree add` if missing) run `git fetch origin`, merge `origin/feat/better-generated-clients` and `origin/master` if they moved, and reinstall nothing else (maven resolves dependencies).
2. Pick the items: the ids given, or `python3 etc/parity-findings.py next <N>` (default 6). Group items that touch the same area into one change set (`slug`: a short kebab-case name of the area, e.g. `datetime-offset`); otherwise one item per set. API items (`API surface: harmonise ...`) of the same class go together.
3. Run the saved workflow: `Workflow({name: "parity-fix", args: {pr: <absolute PR worktree path>, base: <git rev-parse HEAD there>, sets: [...]}})`. If the user gave a token budget (`+2M`), pass it on in the same message so the workflow stops cleanly.
4. When it finishes, push only if `etc/ci-local.sh green` passes on HEAD: run `etc/ci-local.sh check > /tmp/ci-full.txt 2>&1` in the background, then `git push origin feat/better-generated-clients`. The commit guard refuses anything else.
5. Report ONE line per change set (landed / parked / false-positive) and the progress number `python3 etc/parity-findings.py count`.

If CI fails on a commit that passed locally: write the failing job and reason into `<git-common-dir>/parity-halt` (this stops all commits and pushes), find what the local run missed, add that check to `etc/ci-local.sh`, fix, and only then delete the halt file.
