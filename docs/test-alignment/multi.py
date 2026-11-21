#!/usr/bin/env python3
"""Step 5: classify the '| multi' catalogue ids. Writes multi.json. Static only (cannot see run-time case counts)."""
import re, os, json, collections
import scan
HERE = os.path.dirname(os.path.abspath(__file__))
PARAM = re.compile(r'(@ParameterizedTest|@MethodSource|@ValueSource|@CsvSource|\[Theory\]|\[InlineData|\[MemberData|parametrize|\.each\(|\.with\(\[|->with\(|\#\[rstest\]|#\[case|test_case|\bfor\s*\(|\bfor\s+\w+\s+in\b|\.forEach|\.each\b|Enum\.each|for\s+\(?\w+.*\)\s*do|cases\s*:?=|tests\s*:?=|\[\]struct|t\.Run\(|forEach|@TestFactory|DynamicTest|for\s*\{|arguments:|\bcases\b)', re.I)
WRAP = re.compile(r'(PetStore|Admin|ServiceToken|ApiKeyHeader|ApiKeyQuery|InternalApiKey|SessionCookie|LegacyAuth|Basic|Bearer|OAuth2|Authenticator)', re.I)
ids, multi = scan.catalogue()
res = {}
for l in scan.SPEC:
    pats, rx = scan.SPEC[l]
    for f in scan.files(l, pats):
        txt = open(f, errors='replace').read()
        hits = []
        import extract
    break
import extract
by = collections.defaultdict(lambda: collections.defaultdict(list))
for l in scan.SPEC:
    for f, n, o, body, line in extract.locate(l):
        i = scan.ident(n)
        if i not in multi: continue
        pre = open(f, errors='replace').read()[max(0, o - 220):o]
        by[i][l].append(dict(name=n, line=line, param=bool(PARAM.search(pre + body[:900])), body=body))
out = {}
for i in sorted(multi):
    langs = by[i]
    fn_counts = {l: len(v) for l, v in langs.items()}
    multi_fn = {l: v for l, v in langs.items() if len(v) > 1}
    param_langs = sorted(l for l, v in langs.items() if any(x['param'] for x in v))
    # cause of the repeat
    names_differ_only_by_wrapper = {l: len({WRAP.sub('', x['name']) for x in v}) == 1 for l, v in multi_fn.items()}
    if multi_fn and all(names_differ_only_by_wrapper.values()):
        cause = 'per-generated-wrapper repetition of one behaviour (fine)'
    elif multi_fn:
        cause = 'several functions with different names (review)'
    elif param_langs:
        cause = 'one function per language, data-driven in ' + ','.join(param_langs) + ' (parametrised cases, fine)'
    else:
        cause = 'one function per language, no parametrisation marker found (multi may be unnecessary or covers several asserts in one body)'
    promise = ids[i]
    out[i] = dict(promise=promise, functions_per_language=fn_counts, data_driven_in=param_langs, cause=cause,
                  bundles_several_claims=bool(re.search(r'\b(and|or|both|each|every|all)\b', promise, re.I)))
json.dump(out, open(HERE + '/multi.json', 'w'), indent=1)
c = collections.Counter(v['cause'].split(' (')[0] for v in out.values())
print(len(out), c)
