.PHONY: install build test lint format typecheck audit docs clean examples examples-run

install:
	npm install

build: install
	npx tsc

test: install
	npm test

lint: install
	npx eslint .
	npx prettier --check .

format: install
	npx prettier --write .

typecheck: install
	npx tsc --noEmit

audit: install
	npm audit --omit=dev

docs: install
	npx typedoc --out .out/docs --treatWarningsAsErrors

clean:
	rm -rf node_modules dist coverage .out/docs

# Type-checks and compiles every file under examples against the built package,
# which the examples import by its own name.
examples: build
	npx tsc -p tsconfig.examples.json

examples-run: examples
	SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL="$(API_BASE_URL)" SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN=placeholder node .out/examples/quickstart.js
