.PHONY: install build test lint format typecheck docs clean

install:
	pip install --quiet -e . --group dev

build: install

test: install
	python -m pytest test/ -v

lint: install
	ruff check .

format: install
	ruff format .

typecheck: install
	mypy petstore_client/

docs: install
	pdoc --output-dir docs petstore_client

clean:
	find . -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null || true
	rm -rf .pytest .mypy .ruff docs
