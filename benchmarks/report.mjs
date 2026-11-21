// Reads runner rows on stdin and prints one markdown table per language: node report.mjs < results.txt
// Row: lang impl model op ns_per_op bytes_per_op allocs_per_op (one per round).
import { readFileSync } from 'node:fs';

const rows = readFileSync(0, 'utf8').split('\n').map((l) => l.trim().split(/\s+/)).filter((p) => p.length === 7);
const groups = new Map();
for (const [lang, impl, model, op, ns, bytes, allocs] of rows) {
  const key = [lang, model, op, impl].join('|');
  const g = groups.get(key) ?? { ns: [], bytes: bytes, allocs: allocs };
  g.ns.push(Number(ns));
  if (bytes !== '-') { g.bytes = bytes; g.allocs = allocs; }
  groups.set(key, g);
}
const median = (a) => { const s = [...a].sort((x, y) => x - y); const m = s.length >> 1; return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2; };
const fmt = (ns) => (ns >= 1e6 ? `${(ns / 1e6).toFixed(2)} ms` : ns >= 1e3 ? `${(ns / 1e3).toFixed(1)} us` : `${ns.toFixed(0)} ns`);
const langs = [...new Set(rows.map((r) => r[0]))];
for (const lang of langs) {
  console.log(`\n### ${lang}\n`);
  console.log('| model | op | implementation | median | range (min-max) | spread | vs generated | bytes/op | allocs/op |');
  console.log('|---|---|---|---|---|---|---|---|---|');
  for (const model of ['small', 'medium', 'large']) {
    for (const op of ['decode', 'encode']) {
      const base = groups.get([lang, model, op, 'generated'].join('|'));
      const baseMed = base ? median(base.ns) : NaN;
      for (const [key, g] of groups) {
        const [l, m, o, impl] = key.split('|');
        if (l !== lang || m !== model || o !== op) continue;
        const med = median(g.ns);
        const lo = Math.min(...g.ns), hi = Math.max(...g.ns);
        console.log(`| ${model} | ${op} | ${impl} | ${fmt(med)} | ${fmt(lo)} - ${fmt(hi)} | ${(((hi - lo) / med) * 100).toFixed(0)}% | ${impl === 'generated' ? '1.00x' : (med / baseMed).toFixed(2) + 'x'} | ${g.bytes} | ${g.allocs} |`);
      }
    }
  }
}
