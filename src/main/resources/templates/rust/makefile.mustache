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
	RUSTDOCFLAGS="-D warnings" cargo doc --no-deps --target-dir .out/cargo-doc
	rm -rf .out/docs
	cp -r .out/cargo-doc/doc .out/docs

clean:
	cargo clean
