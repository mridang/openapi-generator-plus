#!/usr/bin/env bash
# Runs inside golang:1.26-alpine. Mounts: /golden (generated go SDK, read-only), /fixtures, /bench (this folder).
# Prints rows: lang impl model op ns_per_op bytes_per_op allocs_per_op (one per round).
set -euo pipefail
ROUNDS="${ROUNDS:-5}"
BENCHTIME="${BENCHTIME:-1s}"
cp -r /golden /work && cd /work
cp /bench/bench_test.go /bench/jsonv2_test.go pkg/
mkdir -p bench/mirror && cp /bench/mirror/types.go bench/mirror/
go install github.com/mailru/easyjson/easyjson@v0.9.0 >&2
go get github.com/mailru/easyjson@v0.9.0 >&2
"$(go env GOPATH)/bin/easyjson" -all bench/mirror/types.go >&2
run() {
  go test ./pkg -run '^$' -bench . -benchmem -benchtime "$BENCHTIME" -count "$ROUNDS" "$@" 2>&1 \
    | awk '/^Benchmark/ { split($1, p, "/"); impl=p[2]; sub(/-[0-9]+$/, "", p[4]);
        print "go", impl, p[3], p[4], $3, $(NF-3), $(NF-1) }'
}
run
GOEXPERIMENT=jsonv2 run -bench 'JSONv2'
