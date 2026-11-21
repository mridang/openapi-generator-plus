#!/usr/bin/env python3
"""Deterministic stratified review sample: python3 sample.py <tier> <n> [seed]"""
import json, random, sys, os
HERE = os.path.dirname(os.path.abspath(__file__))
c = json.load(open(HERE + '/clusters.json'))
ex = json.load(open(HERE + '/extras.json'))
body = {(l, r['file'], r['line']): r['body'] for l in ex for r in ex[l]}
tier, n = sys.argv[1], int(sys.argv[2]); seed = int(sys.argv[3]) if len(sys.argv) > 3 else 7
f = {'hi': lambda a: a >= .5, 'mid': lambda a: .42 <= a < .5, 'lo': lambda a: a < .42}[tier]
gs = [(i, g) for i, g in enumerate(c) if len(g["members"]) > 1 and f(g['avg'])]
for i, g in random.Random(seed).sample(gs, min(n, len(gs))):
    print(f"=== cluster#{i} avg={g['avg']} min={g['min']} langs={len(g['members'])}")
    for m in g['members']:
        b = body[(m['lang'], m['file'], m['line'])]
        print(f"  [{m['lang']}] {m['name'][:80]}\n      {b[len(m['name']):len(m['name'])+260] if False else b[:210]}")
