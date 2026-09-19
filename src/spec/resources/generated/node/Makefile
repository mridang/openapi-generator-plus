.PHONY: install build test lint format typecheck docs clean

install:
	npm install

build: install
	npx tsc

test: install
	npx jest --verbose

lint: install
	npx eslint .

format: install
	npx prettier --write .

typecheck: install
	npx tsc --noEmit

docs: install
	npx typedoc --out .out/docs --treatWarningsAsErrors

clean:
	rm -rf node_modules dist coverage .out/docs
