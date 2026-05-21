.PHONY: install build test lint format credo dialyzer docs clean

install:
	mix deps.get

build: install
	mix compile

test: install
	mkdir -p .out/reports
	mix test

lint: install
	mix format --check-formatted
	mix credo --strict

credo: install
	mix credo --strict

dialyzer: install
	mix dialyzer

format: install
	mix format

docs: install
	mix docs

clean:
	mix clean
	rm -rf _build deps doc
