.PHONY: all build test format lint analyse docs clean

all: build

build:
	swift build -Xswiftc -warnings-as-errors

test:
	mkdir -p .out/reports
	swift test --parallel --enable-code-coverage --xunit-output .out/reports/junit.xml
	cp $$(swift test --show-codecov-path 2>/dev/null) .out/coverage.xml 2>/dev/null || true

format:
	swift format --recursive --in-place Sources Tests

lint:
	swift format lint --recursive --strict Sources Tests
	swiftlint lint --strict --config .swiftlint.yml

analyse:
	swift build -Xswiftc -warnings-as-errors -Xswiftc -strict-concurrency=complete

docs:
	swift package generate-documentation

clean:
	swift package clean
