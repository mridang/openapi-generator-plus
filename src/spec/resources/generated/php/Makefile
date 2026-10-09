.PHONY: install build test lint format analyze docs clean

install:
	composer install --no-interaction --prefer-dist

build: install

test: install
	vendor/bin/pest --testdox --parallel

lint: install
	vendor/bin/phpcs

format: install
	vendor/bin/phpcbf || true

analyze: install
	vendor/bin/phpstan analyse

docs: install
	mkdir -p .phpdoc
	test -f .phpdoc/phpDocumentor.phar || curl -fsSL -o .phpdoc/phpDocumentor.phar https://github.com/phpDocumentor/phpDocumentor/releases/latest/download/phpDocumentor.phar
	php .phpdoc/phpDocumentor.phar --config phpdoc.dist.xml --no-interaction -v 2>&1 | tee .phpdoc/phpdoc.log
	! grep -iE 'unable to|an error was detected|could not|^[[:space:]]*WARNING|^[[:space:]]*ERROR' .phpdoc/phpdoc.log | grep -viE 'Writer twig|reports/(errors|deprecated|markers)\.html'
	grep -qiE 'no errors have been found' .out/docs/reports/errors.html

clean:
	rm -rf vendor .phpunit.result.cache .pest.cache .phpdoc docs
