.PHONY: install build test lint format credo dialyzer audit docs clean examples examples-run

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

audit: install
	mix hex.audit

format: install
	mix format

docs: install
	mix docs --warnings-as-errors

clean:
	mix clean
	rm -rf _build deps doc

# Compiles every program under examples/ against the package, warnings being errors.
examples: install
	mix compile
	mkdir -p .out/examples
	elixirc --warnings-as-errors -o .out/examples -pa "_build/dev/lib/*/ebin" examples/*.ex

examples-run: examples
	SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL="$(API_BASE_URL)" SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN=placeholder mix run -r examples/quickstart.ex -e "Quickstart.main()"
