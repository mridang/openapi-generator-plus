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
	# cross-references ambiguous and trips -W. Mark the facade page :no-index:
	# so each object is documented once, in its canonical submodule.
	python -c "import re, pathlib; p = pathlib.Path('.out/docs-src/petstore_client.rst'); p.write_text(re.sub(r'(Module contents\n-+\n\n\.\. automodule:: [\w.]+\n)(?:   :[\w-]+:\n)+', r'\1   :no-index:\n', p.read_text()))"
	sphinx-build -W --keep-going -b html .out/docs-src .out/docs

clean:
	find . -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null || true
	rm -rf .pytest .mypy .ruff .out/docs .out/docs-src
