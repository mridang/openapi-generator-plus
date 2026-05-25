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
	dart doc

clean:
	rm -rf .dart_tool/
	rm -rf build/
	rm -rf doc/
