#!/usr/bin/env python3
"""Step 1: list every test without a catalogue id, per language, with file, line, SUT and assertion subject.
Writes extras.json. Static, no network. Run from anywhere."""
import re, os, json, glob
import scan
G = scan.G
ASSERT = re.compile(r'(assert|expect|should|XCT|Assert|refute|require\.|t\.(Error|Fatal)|equal\(|toBe|toEqual|toThrow|raises|panic)', re.I)

def sut_of(path, lang):
    b = os.path.basename(path)
    b = re.sub(r'\.(java|kt|cs|php|py|rb|ts|dart|go|rs|swift|exs)$', '', b)
    b = re.sub(r'(?i)(^test_?|_?test$|\.test$|Tests?$|_spec$|^Test)', '', b)
    b = re.sub(r'(?i)(^test_?|_?tests?$)', '', b)
    return re.sub(r'[^a-z0-9]', '', b.lower())

def locate(l):
    """yield (file, name, offset, text) for every test, in file order."""
    pats, rx = scan.SPEC[l]
    for f in scan.files(l, pats):
        txt = open(f, errors='replace').read()
        hits = []
        if l == 'rust':
            for m in re.finditer(r'#\[(?:tokio::)?test[^\]]*\]\s*(?:async\s+)?fn\s+(\w+)', txt): hits.append((m.start(), m.group(1)))
        elif l == 'kotlin':
            for m in re.finditer(r'@Test\b(?:[^\n]*\n)+?\s*(?:@DisplayName\("([^"]+)"\)\s*)?(?:suspend\s+)?fun\s+(?:`([^`]+)`|(\w+))', txt):
                hits.append((m.start(), m.group(1) or m.group(2) or m.group(3)))
        else:
            for r in rx:
                for m in re.finditer(r, txt): hits.append((m.start(), m.group(1)))
        hits.sort()
        for i, (o, n) in enumerate(hits):
            end = hits[i + 1][0] if i + 1 < len(hits) else len(txt)
            yield f, n, o, txt[o:min(end, o + 1800)], txt.count('\n', 0, o) + 1

def subject(body):
    for ln in body.split('\n')[1:]:
        if ASSERT.search(ln):
            return re.sub(r'\s+', ' ', ln.strip())[:160]
    return ''

if __name__ == '__main__':
    out = {}
    for l in scan.SPEC:
        rows = []
        for f, n, o, body, line in locate(l):
            if scan.ident(n): continue
            rows.append(dict(name=n, file=os.path.relpath(f, G + '/' + l), line=line, sut=sut_of(f, l), subject=subject(body), body=re.sub(r'\s+', ' ', body)[:700]))
        out[l] = rows
        print(l, len(rows))
    json.dump(out, open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'extras.json'), 'w'), indent=1)
