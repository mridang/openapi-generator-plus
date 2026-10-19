#!/usr/bin/env python3
"""Maintain PARITY_FINDINGS.json (source of truth) and render PARITY_FINDINGS.md.

  parity-findings.py import <journal.jsonl>   one-off: create the JSON from a sweep journal
  parity-findings.py render                   regenerate the markdown from the JSON
  parity-findings.py set <F001,F002,...> <status> [note]   record a status change

Statuses: open, fixed, false-positive, forced, duplicate, deferred.
"""
import json, os, sys, collections

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
JSON_PATH = os.path.join(ROOT, "PARITY_FINDINGS.json")
MD_PATH = os.path.join(ROOT, "PARITY_FINDINGS.md")
SEV = {"critical": 0, "high": 1, "medium": 2, "low": 3}


def load():
    return json.load(open(JSON_PATH, encoding="utf-8"))


def save(data):
    json.dump(data, open(JSON_PATH, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    open(JSON_PATH, "a").write("\n")


def do_import(journal):
    labels, res = {}, {}
    for line in open(journal, encoding="utf-8"):
        try:
            e = json.loads(line)
        except ValueError:
            continue
        if e.get("label") and e.get("agentId"):
            labels[e["agentId"]] = e["label"]
        if e.get("type") == "result":
            lab = e.get("label") or labels.get(e.get("agentId"))
            res[lab] = e.get("result", e.get("value"))
    rows = []
    for lab, v in res.items():
        if lab and lab.startswith("find:") and isinstance(v, dict):
            for f in v.get("findings", []):
                f["lens"] = lab[5:]
                rows.append(f)
    rows.sort(key=lambda f: (SEV.get(f.get("severity"), 9), f["lens"], f.get("title", "")))
    for i, f in enumerate(rows, 1):
        f["id"] = "F%03d" % i
        f["status"] = "open"
        f["note"] = ""
    save({"findings": rows})
    print("imported", len(rows))


def do_set(ids, status, note):
    data = load()
    want = set(ids.split(","))
    n = 0
    for f in data["findings"]:
        if f["id"] in want:
            f["status"] = status
            if note:
                f["note"] = note
            n += 1
    save(data)
    print("updated", n)


def render():
    fs = load()["findings"]
    by_status = collections.Counter(f["status"] for f in fs)
    by_sev = collections.Counter(f["severity"] for f in fs)
    out = ["# Parity findings ledger\n",
           "Source of truth: `PARITY_FINDINGS.json` (edit with `etc/parity-findings.py`). "
           "Findings came from independent reviewers and are CLAIMS until marked otherwise: "
           "they were not deduplicated or verified when imported.\n",
           "Status counts: " + ", ".join("%s %d" % kv for kv in sorted(by_status.items())) + ". "
           "Severity: " + ", ".join("%s %d" % (k, by_sev[k]) for k in SEV if k in by_sev) + ".\n",
           "| ID | Sev | Status | Lens | SDKs | Title |", "|---|---|---|---|---|---|"]
    for f in fs:
        out.append("| %s | %s | %s | %s | %s | %s |" % (
            f["id"], f["severity"], f["status"], f["lens"], ", ".join(f.get("languages", [])),
            f["title"].replace("|", "\\|")))
    out.append("")
    for f in fs:
        out.append("## %s [%s] %s" % (f["id"], f["severity"], f["title"]))
        out.append("- Status: **%s**%s | Lens: %s | Category: %s | Unit-testable (reviewer's view): %s"
                   % (f["status"], (" (%s)" % f["note"]) if f.get("note") else "", f["lens"],
                      f.get("category"), f.get("unit_testable")))
        out.append("- SDKs: %s" % ", ".join(f.get("languages", [])))
        out.append("")
        out.append(f.get("description", "").strip())
        if f.get("suspected_forced_reason"):
            out.append("\nReviewer suspected this is forced: %s" % f["suspected_forced_reason"])
        for ev in f.get("evidence") or []:
            loc = "%s%s" % (ev.get("file"), (":%s" % ev["line"]) if ev.get("line") else "")
            q = (" — " + ev["quote"].replace("\n", " ")[:200]) if ev.get("quote") else ""
            out.append("- `%s`%s" % (loc, q))
        out.append("")
    open(MD_PATH, "w", encoding="utf-8").write("\n".join(out))
    print("rendered", len(fs))


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "import":
        do_import(sys.argv[2])
    elif cmd == "set":
        do_set(sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else "")
    elif cmd == "render":
        pass
    render()
