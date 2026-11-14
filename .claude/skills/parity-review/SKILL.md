---
name: parity-review
description: Review the twelve generated SDKs for parity defects using the catalogue of bug classes already found, and file every verified finding in the single ledger. Use instead of writing review instructions by hand.
argument-hint: "[all | <lang> | <area>]"
---
# /parity-review

1. Load the bug-class catalogue: `python3 etc/parity-findings.py classes` (every review lens L01..L22 with real examples). A bug class found in one language is a lead in the other eleven.
2. Run the saved workflow `parity-review` with `args: {scope: "<all|lang|area>", lenses: [<the lens names that fit the scope>]}`. Finders run on Sonnet, each verified finding on Opus.
3. Write the verified findings (the workflow returns them in ledger format) to a temp JSON file and add them: `python3 etc/parity-findings.py add <file>`, then `python3 etc/parity-findings.py render`, and commit the ledger on the PR branch as `docs: record parity review <scope> in the findings ledger`.
4. Report one line: new, duplicate and rejected counts, and the progress number (`python3 etc/parity-findings.py count`). Never keep findings in any other file.
