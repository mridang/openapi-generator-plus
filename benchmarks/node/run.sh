#!/usr/bin/env bash
# Runs inside node:24-alpine. Mounts: /golden (generated node SDK, read-only), /fixtures, /bench (this folder).
set -euo pipefail
cp -r /golden /work && cd /work
npm install --omit=dev --ignore-scripts --no-audit --no-fund --loglevel=error >&2
mkdir /tools && (cd /tools && npm init -y >/dev/null && npm install --no-audit --no-fund --loglevel=error typescript@5.7 @types/node@24 >&2)
/tools/node_modules/.bin/tsc --typeRoots /tools/node_modules/@types --types node >&2
cp /bench/bench.mjs .
node --expose-gc bench.mjs
