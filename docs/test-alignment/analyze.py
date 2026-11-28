#!/usr/bin/env python3
"""Step 3: turn clusters.json into groups.json (+ totals). Deterministic, no network.
 - area:   kNN vote against the NUMBERED tests (their catalogue area) plus a per-SUT prior
 - id:     <area><next free number after the catalogue's highest in that area>, assigned in group order
 - promise: DRAFT sentence derived from the most descriptive member name (a human must reword before it enters the catalogue)
 - confidence: tier of the average-link score (calibrated by the manual review in review.json)
 - one-offs: recommendation by explicit rules (see ONE_OFF_RULES)"""
import re, os, json, collections, math
import scan, extract, cluster
HERE = os.path.dirname(os.path.abspath(__file__))
G = scan.G
ids, multi = scan.catalogue()
LANGS = sorted(scan.SPEC)
maxnum = collections.defaultdict(int)
for i in ids:
    m = re.fullmatch(r'([a-z]+)(\d{3})', i); maxnum[m.group(1)] = max(maxnum[m.group(1)], int(m.group(2)))
AREAS = sorted(maxnum)

# ---- numbered corpus (for area voting and near-duplicate detection)
num = []
for l in LANGS:
    for f, n, o, body, line in extract.locate(l):
        i = scan.ident(n)
        if not i: continue
        num.append(dict(id=len(num), lang=l, name=n, bid=i, area=re.fullmatch(r'([a-z]+)\d{3}', i).group(1),
                        sut=extract.sut_of(f, l), body=re.sub(r'\s+', ' ', body)[:700]))
for r in num:
    # strip the id token from the name so it does not dominate similarity
    r['ntok'] = [t for t in cluster.name_tokens(re.sub(r'(?i)bhv[\s_]*[a-z]+[\s_]*\d{3}', ' ', r['name'])) if not re.fullmatch(r'bhv\w*', t)]
    r['btok'] = cluster.body_tokens(r['body']); r['s'] = cluster.sut(r['sut'])
items = cluster.build()
allitems = items + num
df = cluster.prepare(allitems)
ex_by_id = {it['id']: it for it in items}
sut_area = collections.defaultdict(collections.Counter)
for r in num: sut_area[r['s']][r['area']] += 1

def vote_area(members):
    votes = collections.Counter()
    for m in members:
        a = ex_by_id[m]
        sc = sorted(((cluster.score(a, b), b) for b in num), key=lambda x: -x[0])[:5]
        for s, b in sc: votes[b['area']] += s
        tot = sum(sut_area[a['s']].values()) or 1
        for ar, c in sut_area[a['s']].items(): votes[ar] += 0.3 * c / tot
    top = votes.most_common(2)
    margin = (top[0][1] - top[1][1]) / top[0][1] if len(top) > 1 and top[0][1] else 1.0
    return top[0][0], round(margin, 2), [a for a, _ in top[:3]]

def nearest_numbered(a, lang):
    best = (0.0, None)
    for b in num:
        if b['lang'] != lang: continue
        s = cluster.score(a, b)
        if s > best[0]: best = (s, b)
    return best

def sentence(name):
    n = re.sub(r'(?i)^(test_?|test\b)', '', name)
    n = ' '.join(cluster.split(n)) if re.search(r'[a-z][A-Z]|_', n) and ' ' not in n else n
    n = re.sub(r'\s+', ' ', n).strip()
    return (n[:1].upper() + n[1:]).rstrip('.')

def promise_of(members):
    # prefer a natural-language title (contains spaces) from node/dart/ruby/elixir/kotlin/php, else the longest name
    pref = {l: k for k, l in enumerate(['node', 'dart', 'ruby', 'elixir', 'kotlin', 'php', 'java', 'csharp', 'swift', 'python', 'go', 'rust'])}
    spaced = [m for m in members if ' ' in m['name'] and not m['name'].startswith('test')]
    pool = spaced or members
    best = sorted(pool, key=lambda m: (pref[m['lang']], len(m['name'])))[0]
    return sentence(best['name']), best['lang']

LANG_SPECIFIC = re.compile(r'(gradle|pom\b|nuget|csproj|composer|gemspec|pubspec|cargo|\bhex\b|mix\b|package\.?json|manifest|readme|skills|lint|rubocop|steep|rbs|dialyzer|mypy|pyright|psr|guzzle|symfony|faraday|urlsession|reqwest|tokio|undici|jackson|ktor|kotlinx|pydantic|dataclass|sendable|linux|macos|swift|goroutine|\bcontext\b|errors?\.as|errors?as|nullable|optional|boxed|lifetime|borrow|trait|crate|namespace|autoload|bundler|devbox|toolchain|compiles?|typespec|typecheck|public surface|constant each lib|frozen|ractor|actor|inspect|__repr__|\brepr\b|debugdescription|hashcode|\bhash\b|equals\b|gc\b|finaliz|dispose|abortsignal|abort\b|signal\b)', re.I)
LANG_SPECIFIC_SUT = {'metadata', 'readme', 'skills', 'psr18apiclientunit', 'webapiclient', 'containershelper', 'bootstrap', 'base64serde', 'requestoption', 'apiclient'}
CONTRACT_SUT = {'objectserializer', 'valueserializer', 'apierror', 'apiexception', 'headerselector', 'serverconfiguration', 'servervariable', 'apikeyauthenticator',
                'bearerauthenticator', 'basicauthenticator', 'schemeauthenticator', 'schemeauthenticators', 'oauth2tokenmanager', 'openidconnectauthenticator',
                'oauth2authorizationcodeauthenticator', 'oauth2clientcredentialsauthenticator', 'oauth2implicitauthenticator', 'oauth2passwordauthenticator',
                'transportoptions', 'configuration', 'apiresult', 'credentials', 'composedschema', 'baseapi', 'defaultapiclientunit', 'defaultapiclient', 'client', 'petapi', 'storeapi', 'iso8601', 'datetime', 'tracecontextutil'}

def one_off(a):
    s, nb = nearest_numbered(a, a['lang'])
    if s >= 0.62:
        return 'possible-duplicate-verify-then-delete', f'near-duplicate of catalogue test {nb["bid"]} ({nb["name"][:60]}) in the same language, score {s:.2f}; confirm by diffing assertions', round(s, 2), nb['bid']
    if a['sut'] in LANG_SPECIFIC_SUT or LANG_SPECIFIC.search(a['name']):
        return 'keep-language-specific', 'packaging/docs/transport-library/idiom-bound subject (matched by rule)', round(s, 2), None
    if a['s'] in CONTRACT_SUT:
        return 'generalise-candidate', 'a contract-level subject (' + a['sut'] + ') with no language-idiom marker in its name; needs triage whether the behaviour is universal', round(s, 2), None
    return 'keep-language-specific', 'subject not part of the shared contract surface', round(s, 2), None

if __name__ == '__main__':
    cl = json.load(open(HERE + '/clusters.json'))
    groups = []; one = []
    for g in cl:
        langs = sorted({m['lang'] for m in g['members']})
        if len(langs) >= 2: groups.append(g)
        else: one.append(g)
    # singles that were attached as siblings are inside groups already; singles left are one-offs
    out_groups = []
    nextnum = dict(maxnum)
    for g in groups:
        area, margin, runner = vote_area([m['id'] for m in g['members']])
        nextnum[area] += 1
        pr, src = promise_of(g['members'])
        tier = 'high' if g['avg'] >= .5 else 'medium' if g['avg'] >= .42 else 'low'
        langs = sorted({m['lang'] for m in g['members']})
        out_groups.append(dict(
            proposed_id=f'{area}{nextnum[area]:03d}', area=area, area_margin=margin, area_runners_up=runner[1:],
            promise_draft=pr, promise_source=src, confidence=tier, avg_link=g['avg'], min_link=g['min'],
            languages=langs, languages_missing=[l for l in LANGS if l not in langs], n_languages=len(langs),
            members=[{k: m[k] for k in ('lang', 'name', 'file', 'line', 'sut')} for m in g['members']],
            same_language_siblings=[{k: m[k] for k in ('lang', 'name', 'file', 'line', 'score')} for m in g['siblings']]))
    one_offs = []
    for g in one:
        m = g['members'][0]; a = ex_by_id[m['id']]
        rec, why, sim, near = one_off(a)
        one_offs.append(dict(lang=m['lang'], name=m['name'], file=m['file'], line=m['line'], sut=m['sut'], subject=m['subject'], recommendation=rec, reason=why, nearest_catalogue_score=sim, nearest_catalogue_id=near))
    json.dump(dict(languages=LANGS, new_areas=[], groups=out_groups, one_offs=one_offs), open(HERE + '/groups.json', 'w'), indent=1)
    print(len(out_groups), len(one_offs))
