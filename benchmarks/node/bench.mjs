// Runs against the compiled generated SDK in ./dist. Prints rows:
// lang impl model op ns_per_op bytes_per_op allocs_per_op (one per round; bytes/allocs are "-" on node).
import { readFileSync } from 'node:fs';
import { performance } from 'node:perf_hooks';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
require('reflect-metadata');
const { ObjectSerializer } = require('./dist/src/object-serializer.js');
const { Pet, Tag } = require('./dist/src/index.js');

const ROUNDS = Number(process.env.ROUNDS ?? 5);
const TARGET_MS = Number(process.env.TARGET_MS ?? 700);
const text = Object.fromEntries(
  ['small', 'medium', 'large'].map((n) => [n, readFileSync(`/fixtures/${n}.json`, 'utf8')]),
);

const sink = { v: null };

function mapPet(o) {
  return new Pet({
    id: o.id,
    name: o.name,
    category: o.category === undefined ? undefined : { id: o.category.id, name: o.category.name },
    photoUrls: new Set(o.photoUrls),
    tags: o.tags?.map((t) => new Tag({ id: t.id, name: t.name })),
    status: o.status,
    homepageUrl: o.homepageUrl,
    thumbnailRef: o.thumbnailRef,
    ownerEmail: o.ownerEmail,
    weightKg: o.weightKg,
    nickname: o.nickname,
  });
}
function unmapPet(p) {
  return {
    id: p.id,
    name: p.name,
    category: p.category,
    photoUrls: [...p.photoUrls],
    tags: p.tags?.map((t) => ({ id: t.id, name: t.name })),
    status: p.status,
    homepageUrl: p.homepageUrl,
    thumbnailRef: p.thumbnailRef,
    ownerEmail: p.ownerEmail,
    weightKg: p.weightKg,
    nickname: p.nickname,
  };
}

const impls = {
  generated: {
    decode: {
      small: (s) => ObjectSerializer.deserialize(ObjectSerializer.parseJson(s), Tag),
      medium: (s) => ObjectSerializer.deserialize(ObjectSerializer.parseJson(s), Pet),
      large: (s) => ObjectSerializer.deserializeArray(ObjectSerializer.parseJson(s), Pet),
    },
    encode: (v) => ObjectSerializer.serialize(v),
  },
  manual: {
    decode: {
      small: (s) => { const o = JSON.parse(s); return new Tag({ id: o.id, name: o.name }); },
      medium: (s) => mapPet(JSON.parse(s)),
      large: (s) => JSON.parse(s).map(mapPet),
    },
    encode: (v) => JSON.stringify(Array.isArray(v) ? v.map(unmapPet) : v instanceof Pet ? unmapPet(v) : { id: v.id, name: v.name }),
  },
  native: {
    decode: { small: JSON.parse, medium: JSON.parse, large: JSON.parse },
    encode: (v) => JSON.stringify(v),
  },
};

function timeIt(fn) {
  for (let i = 0; i < 200; i++) sink.v = fn();
  let n = 1;
  for (;;) {
    const t0 = performance.now();
    for (let i = 0; i < n; i++) sink.v = fn();
    const dt = performance.now() - t0;
    if (dt > 50) { n = Math.max(1, Math.round((n * TARGET_MS) / dt)); break; }
    n *= 4;
  }
  const t0 = performance.now();
  for (let i = 0; i < n; i++) sink.v = fn();
  return ((performance.now() - t0) * 1e6) / n;
}

for (let round = 0; round < ROUNDS; round++) {
  for (const [impl, def] of Object.entries(impls)) {
    for (const model of ['small', 'medium', 'large']) {
      const s = text[model];
      const decode = def.decode[model];
      console.log('node', impl, model, 'decode', timeIt(() => decode(s)).toFixed(1), '-', '-');
      const value = impl === 'native' ? JSON.parse(s) : impls.manual.decode[model](s);
      const model_ = impl === 'generated' ? impls.generated.decode[model](s) : value;
      console.log('node', impl, model, 'encode', timeIt(() => def.encode(model_)).toFixed(1), '-', '-');
    }
  }
}
// Correctness: the generated encoder and the manual mapper agree on the medium payload.
const a = JSON.parse(impls.generated.encode(impls.generated.decode.medium(text.medium)));
const b = JSON.parse(text.medium);
if (JSON.stringify(a) !== JSON.stringify(b)) console.error('MISMATCH generated vs fixture', JSON.stringify(a));
