#!/usr/bin/env python3
"""Step 2: deterministic cross-language clustering of the un-numbered tests (reads extras.json, writes clusters.json).

Pipeline
  normalise name -> tokens (split camelCase/snake/space, lowercase, drop filler, synonym-fold, stem)
  score(a,b)     -> 0.65 * idf-cosine(name tokens) + 0.25 * idf-cosine(body literal tokens)
                    + 0.10 if same normalised SUT (file-level subject)   [only pairs from different languages]
  clustering     -> greedy agglomerative: edges taken in descending score (ties broken by stable key), two clusters
                    merge only if they share no language and the AVERAGE link score is >= MERGE_T
No randomness, no network. Output is byte-identical between runs.
"""
import re, os, json, math, collections, itertools
HERE = os.path.dirname(os.path.abspath(__file__))
MERGE_T = 0.36
FILLER = set('test tests should it when a an the is are to of and with for in on by that does do be as at its this from then can will'.split())
SYN = {'throws': 'reject', 'throw': 'reject', 'raises': 'reject', 'raise': 'reject', 'rejects': 'reject', 'reject': 'reject',
       'refuses': 'reject', 'refuse': 'reject', 'fails': 'reject', 'fail': 'reject', 'error': 'reject', 'errors': 'reject',
       'exception': 'reject', 'invalid': 'reject', 'returns': 'return', 'yields': 'return', 'gives': 'return', 'produces': 'return',
       'nil': 'null', 'none': 'null', 'nul': 'null', 'undefined': 'null', 'null': 'null',
       'str': 'string', 'strings': 'string', 'repr': 'tostring', 'inspect': 'tostring', 'debug': 'tostring', 'display': 'tostring',
       'tostring': 'tostring', 'masks': 'redact', 'mask': 'redact', 'redacts': 'redact', 'redacted': 'redact', 'hides': 'redact',
       'leak': 'redact', 'leaks': 'redact', 'secret': 'secret', 'secrets': 'secret', 'blank': 'empty', 'whitespace': 'empty',
       'sent': 'send', 'sends': 'send', 'emits': 'send', 'writes': 'write', 'written': 'write', 'urlencoded': 'urlencode',
       'encodes': 'encode', 'encoded': 'encode', 'decodes': 'decode', 'decoded': 'decode', 'parses': 'parse', 'parsed': 'parse'}
def stem(w):
    if w in SYN: return SYN[w]
    for suf in ('ing', 'ed', 'es', 's', 'ly'):
        if len(w) > len(suf) + 3 and w.endswith(suf): return w[:-len(suf)]
    return w
def split(s):
    s = re.sub(r'([a-z0-9])([A-Z])', r'\1 \2', s)
    s = re.sub(r'([A-Z]+)([A-Z][a-z])', r'\1 \2', s)
    return [w for w in re.split(r'[^A-Za-z0-9]+', s.lower()) if w]
def name_tokens(name):
    return [stem(w) for w in split(name) if w not in FILLER and not re.fullmatch(r'\d+', w) or re.fullmatch(r'\d{2,}', w)]
def body_tokens(body):
    toks = set()
    for m in re.finditer(r'"((?:[^"\\]|\\.){2,40})"|\'((?:[^\'\\]|\\.){2,40})\'', body):
        v = m.group(1) or m.group(2)
        toks.add('lit:' + re.sub(r'\s+', ' ', v.lower()))
    for m in re.finditer(r'\b\d{3,}\b', body): toks.add('num:' + m.group(0))
    return toks
SUT_ALIAS = {'schemeauthenticators': 'schemeauthenticator', 'defaultapiclientunit': 'apiclient', 'defaultapiclient': 'apiclient',
             'psr18apiclientunit': 'apiclient', 'webapiclient': 'apiclient', 'apiclient': 'apiclient', 'apiexception': 'apierror',
             'model': 'models', 'modeldoc': 'models', 'iso8601': 'datetime'}
def sut(s): return SUT_ALIAS.get(s, s)

def build():
    d = json.load(open(os.path.join(HERE, 'extras.json')))
    items = []
    for lang in sorted(d):
        for i, r in enumerate(d[lang]):
            items.append(dict(id=len(items), lang=lang, **r, ntok=name_tokens(r['name']), btok=body_tokens(r['body']), s=sut(r['sut'])))
    return items

def vec(tokens, idf):
    c = collections.Counter(tokens)
    v = {t: (1 + math.log(n)) * idf.get(t, 1.0) for t, n in c.items()}
    nrm = math.sqrt(sum(x * x for x in v.values())) or 1.0
    return {t: x / nrm for t, x in v.items()}
def cos(a, b):
    if len(a) > len(b): a, b = b, a
    return sum(x * b.get(t, 0.0) for t, x in a.items())

def prepare(items):
    n = len(items); df = collections.Counter(); bdf = collections.Counter()
    for it in items:
        df.update(set(it['ntok'])); bdf.update(it['btok'])
    idf = {t: math.log((n + 1) / (c + 1)) + 1 for t, c in df.items()}
    bidf = {t: math.log((n + 1) / (c + 1)) + 1 for t, c in bdf.items()}
    for it in items:
        it['nv'] = vec(it['ntok'], idf); it['bv'] = vec(sorted(it['btok']), bidf)
    return df

def score(a, b):
    return 0.65 * cos(a['nv'], b['nv']) + 0.25 * cos(a['bv'], b['bv']) + (0.10 if a['s'] == b['s'] else 0.0)

def edges(items, df):
    inv = collections.defaultdict(list)
    cap = 400
    for it in items:
        for t in it['nv']:
            if df[t] <= cap: inv[t].append(it['id'])
        for t in it['bv']:
            inv['b' + t].append(it['id'])
    cand = set()
    for t, ids in inv.items():
        if len(ids) > cap: continue
        for x, y in itertools.combinations(ids, 2):
            if items[x]['lang'] != items[y]['lang']: cand.add((x, y))
    out = []
    for x, y in cand:
        s = score(items[x], items[y])
        if s >= MERGE_T: out.append((-round(s, 6), x, y))
    out.sort()
    return out

def cluster(items, es):
    members = {i['id']: [i['id']] for i in items}; where = {i['id']: i['id'] for i in items}
    pair = {}
    for ns, x, y in es: pair[(x, y)] = -ns
    for ns, x, y in es:
        cx, cy = where[x], where[y]
        if cx == cy: continue
        lx = {items[m]['lang'] for m in members[cx]}; ly = {items[m]['lang'] for m in members[cy]}
        if lx & ly: continue
        tot = 0.0
        for a in members[cx]:
            for b in members[cy]:
                tot += pair.get((a, b), pair.get((b, a), 0.0))
        avg = tot / (len(members[cx]) * len(members[cy]))
        if avg < MERGE_T: continue
        members[cx] += members[cy]
        for m in members[cy]: where[m] = cx
        del members[cy]
    return [sorted(v) for v in members.values()], members, where

def attach(items, es, members, where):
    """Second pass: a leftover single test whose best cross-language neighbour (score >= ATTACH_T) sits in a cluster that
    already has a test of the leftover's language is attached to that cluster as a same-language SIBLING (not counted as
    coverage of another language: it is either a parametrised case of the same behaviour or a distinct variant)."""
    best = {}
    for ns, x, y in es:  # es sorted by descending score, so first hit is best
        for a, b in ((x, y), (y, x)):
            if a not in best: best[a] = (-ns, b)
    sib = {}
    for it in items:
        c = where[it['id']]
        if len(members[c]) != 1 or it['id'] not in best: continue
        sc, b = best[it['id']]
        cb = where[b]
        if sc >= ATTACH_T and len(members[cb]) > 1 and it['lang'] in {items[m]['lang'] for m in members[cb]}:
            sib[it['id']] = (cb, round(sc, 3))
    return sib

ATTACH_T = 0.40

if __name__ == '__main__':
    items = build(); df = prepare(items); es = edges(items, df); cl, members, where = cluster(items, es)
    sib = attach(items, es, members, where)
    pr = {}
    for ns, x, y in es: pr[(x, y)] = -ns
    def link(g, f):
        v = [pr.get((a, b), pr.get((b, a), 0.0)) for a, b in itertools.combinations(g, 2)]
        return round(f(v), 3) if v else 0.0
    out = []
    for g in cl:
        core = [m for m in g if m not in sib]
        sibs = [m for m in sib if sib[m][0] == where[g[0]] and where[m] == m and m not in g] if len(g) > 1 else []
        if len(g) == 1 and g[0] in sib: continue
        row = lambda m: dict(id=m, lang=items[m]['lang'], name=items[m]['name'], file=items[m]['file'], line=items[m]['line'], sut=items[m]['sut'], subject=items[m]['subject'])
        out.append(dict(avg=link(g, lambda v: sum(v) / len(v)), min=link(g, min), members=[row(m) for m in g],
                        siblings=[dict(row(m), score=sib[m][1]) for m in sorted(sibs)]))
    out.sort(key=lambda g: (-len({m['lang'] for m in g['members']}), g['members'][0]['id']))
    json.dump(out, open(os.path.join(HERE, 'clusters.json'), 'w'), indent=1)
    sz = collections.Counter(len({m['lang'] for m in g['members']}) for g in out)
    print('items', len(items), 'edges', len(es), 'clusters', len(out), 'siblings attached', len(sib)); print(sorted(sz.items()))
