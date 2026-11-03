.PHONY: all get test lint analyze format docs clean

all: get

get:
	dart pub get

test:
	mkdir -p .out/reports
	dart test --coverage=.out/coverage --reporter json | dart run junitreport:tojunit --output .out/reports/junit.xml
	dart run coverage:format_coverage --lcov --in=.out/coverage --out=.out/coverage.xml --report-on=lib

analyze:
	dart analyze --fatal-warnings --fatal-infos

lint: analyze

format:
	dart format .

docs:
	@set -e; \
	out=$$(dart doc --output .out/docs --validate-links 2>&1); \
	status=$$?; \
	printf '%s\n' "$$out"; \
	if [ $$status -ne 0 ]; then exit $$status; fi; \
	if printf '%s\n' "$$out" | grep -i 'warning:' | grep -vi 'deprecated' | grep -q .; then \
		echo 'docs: dartdoc emitted warnings (see above)'; \
		exit 1; \
	fi

clean:
	rm -rf .dart_tool/
	rm -rf build/
	rm -rf doc/
