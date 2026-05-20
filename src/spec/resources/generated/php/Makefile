.PHONY: install build test lint format analyze docs clean

install:
	composer install --no-interaction --prefer-dist

build: install

test: install
	vendor/bin/phpunit --testdox

lint: install
	vendor/bin/phpcs

format: install
	vendor/bin/phpcbf || true

analyze: install
	vendor/bin/phpstan analyse

docs: install
	vendor/bin/phpdoc run -d lib -t docs

clean:
	rm -rf vendor .phpunit.result.cache docs
