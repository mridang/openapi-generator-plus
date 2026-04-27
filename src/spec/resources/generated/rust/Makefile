.PHONY: all build test lint format clean

all: build

format:
	cargo fmt

lint:
	cargo clippy -- -D warnings

build:
	cargo build

test:
	cargo test

clean:
	cargo clean
