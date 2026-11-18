#!/usr/bin/env bash
# Runs inside python:3.14-alpine. Mounts: /golden (generated python SDK, read-only), /fixtures, /bench (this folder).
set -euo pipefail
cp -r /golden /work && cd /work
pip install -q --no-cache-dir --disable-pip-version-check "pydantic>=2.11,<3" python-dateutil "urllib3>=2.1,<3" orjson >&2
cp /bench/bench.py .
python bench.py
