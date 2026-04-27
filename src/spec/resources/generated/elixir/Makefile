.PHONY: install build test lint format clean

install:
	mix deps.get

build: install
	mix compile

test: install
	mix test

lint: install
	mix format --check-formatted

format: install
	mix format

clean:
	mix clean
	rm -rf _build deps
