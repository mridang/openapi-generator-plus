.PHONY: install build test lint format typecheck docs clean

install:
	pip install --quiet -e . --group dev

build: install

test: install
	python -m pytest -v

lint: install
	ruff format --check .
	ruff check .

format: install
	# Mirror the generator's own formatter step (getFormatterCommands):
	# strip the unused imports the wide model import-block emits, THEN format.
	# Without the first line a `make generate` leaves imports ruff -> lint fails.
	ruff check --select F401,F811 --fix --exit-zero --quiet .
	ruff format .

typecheck: install
	# spec/ holds hand-written specs; check it only when it has python files.
	mypy petstore_client/ test/ $(if $(shell find spec -name '*.py' -print -quit 2>/dev/null),spec/)

docs: install
	rm -rf .out/docs-src .out/docs
	mkdir -p .out/docs-src
	printf '%s\n' \
		'import os' \
		'import sys' \
		'' \
		"sys.path.insert(0, os.path.abspath('../..'))" \
		'' \
		"project = 'petstore_client'" \
		"author = 'petstore_client'" \
		"release = '1.0.0'" \
		'' \
		'extensions = [' \
		"    'sphinx.ext.autodoc'," \
		"    'sphinx.ext.napoleon'," \
		"    'sphinx.ext.autosummary'," \
		']' \
		'' \
		'autosummary_generate = True' \
		'autodoc_default_options = {' \
		"    'members': True," \
		"    'undoc-members': True," \
		"    'show-inheritance': True," \
		'}' \
		'napoleon_google_docstring = True' \
		'napoleon_numpy_docstring = True' \
		'' \
		"html_theme = 'furo'" \
		> .out/docs-src/conf.py
	printf '%s\n' \
		'petstore_client' \
		'=========================================' \
		'' \
		'.. toctree::' \
		'   :maxdepth: 2' \
		'   :caption: Contents:' \
		'' \
		'   modules' \
		> .out/docs-src/index.rst
	sphinx-apidoc -o .out/docs-src petstore_client -f
	# The top-level package __init__ is a pure re-export facade: every public
	# class is also defined in (and canonical to) a submodule. Documenting the
	# facade's members too registers each object twice, which makes Sphinx
	# cross-references ambiguous and trips -W. Mark the facade page :no-members:
	# so each object is documented once, in its canonical submodule.
	python -c "import pathlib, re; pat = re.compile(r'(Module contents\n-+\n\n\.\. automodule:: [\w.]+\n)(?:   :[\w-]+:\n)+'); pages = ['.'.join(init.parent.parts) + '.rst' for init in pathlib.Path('petstore_client').rglob('__init__.py')]; [page.write_text(pat.sub(r'\1   :no-members:\n', page.read_text())) for page in map(lambda name: pathlib.Path('.out/docs-src', name), pages) if page.exists()]"
	sphinx-build -W --keep-going -b html .out/docs-src .out/docs

clean:
	find . -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null || true
	rm -rf .pytest .mypy .ruff .out/docs .out/docs-src
