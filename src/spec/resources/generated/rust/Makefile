.PHONY: all build test lint format audit docs clean examples examples-run

all: build

format:
	cargo fmt

lint:
	cargo fmt -- --check
	cargo clippy --all-targets --all-features -- -D warnings

audit:
	cargo deny check

build:
	cargo build

test:
	cargo llvm-cov nextest --all-features --cobertura --output-path .out/coverage.xml --profile ci

docs:
	RUSTDOCFLAGS="-D warnings" cargo doc --no-deps --target-dir .out/cargo-doc
	rm -rf .out/docs
	cp -r .out/cargo-doc/doc .out/docs

clean:
	cargo clean

# Builds every program under examples/ as a cargo example target.
examples:
	cargo build --examples

examples-run: examples
	PETSTORE_BASE_URL="$(API_BASE_URL)" PETSTORE_ACCESS_TOKEN=placeholder cargo run --example quickstart
