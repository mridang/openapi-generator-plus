.PHONY: all build test lint format audit docs clean

all: build

format:
	cargo fmt

lint:
	cargo clippy --all-targets --all-features -- -D warnings

audit:
	cargo deny check

build:
	cargo build

test:
	cargo llvm-cov nextest --cobertura --output-path .out/coverage.xml --profile ci

docs:
	cargo doc --no-deps

clean:
	cargo clean
