#!/usr/bin/env python3
"""Compact view of a language's failed tests from the kept JUnit XML reports.

  etc/test-failures.py <lang>               summary plus failures grouped by message
  etc/test-failures.py <lang> --detail <s>  trimmed stack of the first failing test whose name contains <s>

Reads target/test-reports/<lang>/*.xml (written by the client specs). Prints a few dozen
lines at most, so callers never have to read raw test output.
"""
import re, sys, glob, collections
import xml.etree.ElementTree as ET

def load(path):
    raw = open(path, "rb").read().decode("utf-8", "replace")
    raw = re.sub("[\x00-\x08\x0b\x0c\x0e-\x1f\ufffe\uffff]", "?", raw)
    return ET.fromstring(raw.encode("utf-8"))

def norm(m):
    m = re.sub(r"0x[0-9a-f]+|\b\d+(\.\d+)?\b", "N", m)
    return re.sub(r"\s+", " ", m).strip()[:120]

def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    lang = sys.argv[1]
    detail = sys.argv[sys.argv.index("--detail") + 1] if "--detail" in sys.argv else None
    files = glob.glob(f"target/test-reports/{lang}/*.xml")
    if not files:
        sys.exit(f"no reports for {lang}: run its client spec first")
    total = bad = skipped = 0
    groups = collections.OrderedDict()
    for f in files:
        for tc in load(f).iter("testcase"):
            total += 1
            name = f'{tc.get("classname", "")}.{tc.get("name", "")}'.strip(".")
            fail = tc.find("failure")
            if fail is None:
                fail = tc.find("error")
            if tc.find("skipped") is not None:
                skipped += 1
            if fail is None:
                continue
            bad += 1
            text = (fail.get("message") or "") + "\n" + (fail.text or "")
            if detail is not None:
                if detail in name:
                    print(name)
                    print("\n".join(text.strip().splitlines()[:14]))
                    return
                continue
            msg = fail.get("message") or ""
            if not msg.strip() or "see stacktrace" in msg:
                body = [l.strip() for l in (fail.text or "").splitlines() if l.strip()]
                msg = next((l for l in body if "Expected" in l or "Exception" in l or "Error" in l), body[0] if body else msg)
            first = (msg.splitlines() or [""])[0]
            groups.setdefault(norm(first), []).append((name, first))
    if detail is not None:
        sys.exit(f"no failing test matching {detail}")
    print(f"{lang}: {total} tests, {bad} failed, {skipped} skipped")
    for key, items in sorted(groups.items(), key=lambda kv: -len(kv[1])):
        short = [re.sub(r".*\.", "", n)[:60] for n, _ in items[:3]]
        print(f"[{len(items)}x] {items[0][1][:150]}")
        print("      e.g. " + "; ".join(short))

main()
