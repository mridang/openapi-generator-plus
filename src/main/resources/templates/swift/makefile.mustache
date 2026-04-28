.PHONY: all build test format lint docs clean

all: build

build:
	swift build

test:
	mkdir -p .out/reports
	swift test --enable-code-coverage --xunit-output .out/reports/junit.xml
	cp $$(swift test --show-codecov-path 2>/dev/null) .out/coverage.xml 2>/dev/null || true

format:
	swift format --recursive --in-place Sources Tests

lint:
	swift format --recursive --check Sources Tests

docs:
	swift package generate-documentation

clean:
	swift package clean
