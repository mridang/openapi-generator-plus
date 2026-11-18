// Writes the three benchmark payloads next to this file: node generate.mjs
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = dirname(fileURLToPath(import.meta.url));

function pet(i) {
  return {
    id: 1000 + i,
    name: `doggie-${i}`,
    category: { id: i % 7, name: `category-${i % 7}` },
    photoUrls: [
      `https://example.com/photos/${i}-a.png`,
      `https://example.com/photos/${i}-b.png`,
      `https://example.com/photos/${i}-c.png`,
    ],
    tags: [
      { id: i * 3, name: 'friendly' },
      { id: i * 3 + 1, name: 'house-trained' },
      { id: i * 3 + 2, name: `litter-${i}` },
    ],
    status: ['available', 'pending', 'sold'][i % 3],
    homepageUrl: `https://example.com/pets/${i}`,
    thumbnailRef: `/assets/thumb-${i}.png`,
    ownerEmail: `owner${i}@example.com`,
    weightKg: 12.345 + i,
    nickname: `rex${'abcdefghij'[i % 10]}`,
  };
}

const small = { id: 42, name: 'friendly' };
const medium = pet(1);
const large = Array.from({ length: 200 }, (_, i) => pet(i));

for (const [name, value] of Object.entries({ small, medium, large })) {
  writeFileSync(join(dir, `${name}.json`), JSON.stringify(value));
}
