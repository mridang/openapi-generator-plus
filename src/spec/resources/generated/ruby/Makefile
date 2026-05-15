.PHONY: install build test lint format typecheck docs clean

install:
	bundle install --quiet

build: install

test: install
	bundle exec rake test

lint: install
	bundle exec rubocop --format simple

format: install
	bundle exec rubocop -A

typecheck: install
	bundle exec steep check

docs: install
	bundle exec yard doc

clean:
	rm -rf .bundle vendor doc
