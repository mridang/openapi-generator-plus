#!/usr/bin/env bash
# Runs inside composer:2 (PHP 8.5 CLI with composer). Mounts: /golden (generated php SDK, read-only), /fixtures, /bench.
set -euo pipefail
cp -r /golden /work && cd /work
composer install --no-dev --no-interaction --no-progress --quiet --ignore-platform-reqs --no-scripts >&2
cp /bench/bench.php .
php -d opcache.enable_cli=1 -d opcache.jit=tracing -d opcache.jit_buffer_size=64M -d memory_limit=1G bench.php
