.PHONY: all build test lint format docs clean

all: build

format:
	cargo fmt

lint:
	cargo clippy -- -D warnings

build:
	cargo build

test:
	cargo test

docs:
	cargo doc --no-deps

clean:
	cargo clean
