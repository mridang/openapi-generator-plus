#!/usr/bin/env python3
"""Maintain PARITY_FINDINGS.json (source of truth) and render PARITY_FINDINGS.md.

  parity-findings.py import <journal.jsonl>   one-off: create the JSON from a sweep journal
  parity-findings.py render                   regenerate the markdown from the JSON
  parity-findings.py set <F001,F002,...> <status> [note]   record a status change
  parity-findings.py show <F001,F002,...>     print full details of the given findings
  parity-findings.py apply <results.json> <batch>   record per-language outcomes from a fix batch
  parity-findings.py decision <D06,D11,...> [language]   print decisions from PARITY_DECISIONS.json
  parity-findings.py discovered <results.json> <batch>   list the new issues a batch reported

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


def do_show(ids):
    want = ids.split(",")
    byid = {f["id"]: f for f in load()["findings"]}
    for i in want:
        f = byid.get(i)
        if not f:
            print("%s: not found" % i)
            continue
        print("=" * 78)
        print("%s [%s] %s" % (f["id"], f["severity"], f["title"]))
        print("status=%s lens=%s category=%s sdks=%s" % (f["status"], f["lens"], f.get("category"), ",".join(f.get("languages", []))))
        print(f.get("description", "").strip())
        if f.get("suspected_forced_reason"):
            print("suspected forced: " + f["suspected_forced_reason"])
        for ev in f.get("evidence") or []:
            print("  - %s%s %s" % (ev.get("file"), (":%s" % ev["line"]) if ev.get("line") else "", (ev.get("quote") or "")[:300].replace("\n", " ")))


# Outcomes that count as "this language is done" for a finding.
DONE = {"fixed", "duplicate", "false-positive", "forced"}


def do_apply(path, batch):
    """Merge per-language outcomes into the ledger and derive each finding's status.

    A finding is fixed only when every SDK it names has a done outcome; otherwise it is
    partial. Findings nobody touched stay open.
    """
    res = json.load(open(path, encoding="utf-8"))
    data = load()
    byid = {f["id"]: f for f in data["findings"]}
    for r in res:
        f = byid.get(r["id"])
        if not f:
            continue
        f.setdefault("outcomes", {})[r["language"]] = {
            "outcome": r["outcome"], "note": r.get("note", ""), "batch": batch,
            "test": r.get("test", ""), "files": r.get("files", []),
        }
        f["batch"] = batch
    # New issues an agent noticed but did not fix become ledger entries so they are not lost.
    seen = {(f.get("lens"), f.get("description")) for f in data["findings"]}
    nxt = max(int(f["id"][1:]) for f in data["findings"]) + 1
    for r in res:
        if r["outcome"] != "discovered":
            continue
        key = ("batch%d-discovered" % batch, r["note"])
        if key in seen:
            continue
        seen.add(key)
        title = r["note"].split(". ")[0].rstrip(".")
        f = {"id": "F%03d" % nxt, "severity": "medium", "title": title[:200], "lens": key[0],
             "category": "discovered", "languages": [r["language"]], "description": r["note"],
             "evidence": [], "unit_testable": None, "status": "open", "note": "reported by the %s fix agent, unverified" % r["language"],
             "batch": None}
        nxt += 1
        data["findings"].append(f)
        byid[f["id"]] = f
    for f in byid.values():
        oc = f.get("outcomes")
        if not oc:
            continue
        langs = sorted(set(f.get("languages", [])))
        done = {l for l, o in oc.items() if o["outcome"] in DONE}
        outs = {o["outcome"] for o in oc.values()}
        if outs <= {"false-positive"}:
            f["status"] = "false-positive"
        elif outs <= {"forced"}:
            f["status"] = "forced"
        elif langs and set(langs) <= done:
            f["status"] = "fixed"
        elif done & {l for l, o in oc.items() if o["outcome"] in ("fixed", "duplicate")}:
            f["status"] = "partial"
            f["note"] = "done in %s; still open for %s" % (", ".join(sorted(done)), ", ".join(sorted(set(langs) - done)) or "-")
        elif outs & {"needs-spec"}:
            f["status"] = "needs-spec"
        elif outs & {"too-big"}:
            f["status"] = "deferred"
        else:
            f["status"] = "open"
    save(data)
    print("applied", len(res))


def do_discovered(path, batch):
    res = json.load(open(path, encoding="utf-8"))
    for r in res:
        if r["outcome"] == "discovered":
            print("[%s] %s" % (r["language"], r["note"]))


def do_decision(ids, language):
    decs = json.load(open(os.path.join(ROOT, "PARITY_DECISIONS.json"), encoding="utf-8"))
    for ref in ids.split(","):
        n = int(ref.strip().lstrip("Dd"))
        d = decs[n - 1]
        print("=" * 78)
        print("D%02d %s" % (n, d["title"]))
        print("ledger ids: %s | still true: %s | effort: %s | wire-visible: %s" % (" ".join(d["ids"]), d["still_true"], d["effort"], d.get("wire_visible")))
        print("\nCURRENT STATE:\n" + (d.get("current_state") or ""))
        print("\nCANONICAL:\n" + d["canonical"])
        print("\nWHY:\n" + d["rationale"])
        for pl in d["per_language"]:
            if language and pl["language"] != language:
                continue
            print("\n[%s] %s %s %s" % (pl["language"], "ALREADY CANONICAL" if pl.get("already_canonical") else "CHANGE:", pl["change"], ("(" + pl["files"] + ")") if pl.get("files") else ""))
        print("\nTESTS:\n" + d["tests"])
        if (d.get("spec_yaml") or "").strip():
            print("\nSPEC FIXTURE (already merged into the petstore spec; see PARITY_FIXTURES.md):\n" + d["spec_yaml"])


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
    elif cmd == "apply":
        do_apply(sys.argv[2], int(sys.argv[3]))
    elif cmd == "discovered":
        do_discovered(sys.argv[2], int(sys.argv[3]))
        sys.exit(0)
    elif cmd == "decision":
        do_decision(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
        sys.exit(0)
    elif cmd == "show":
        do_show(sys.argv[2])
        sys.exit(0)
    elif cmd == "render":
        pass
    render()
