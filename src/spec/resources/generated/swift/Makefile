.PHONY: all build test format lint analyse docs clean examples examples-run

# Warnings are errors in this package's own debug builds; see Package.swift.
export SWIFT_SDK_STRICT_WARNINGS = 1

all: build

build:
	swift build -Xswiftc -warnings-as-errors

test:
	mkdir -p .out/reports
	swift test --parallel --enable-code-coverage --xunit-output .out/reports/junit.xml
	cp $$(swift test --show-codecov-path 2>/dev/null) .out/coverage.xml 2>/dev/null || true

format:
	swift format --recursive --in-place Sources Tests examples

lint:
	swift format lint --recursive --strict Sources Tests examples
	swiftlint lint --strict --config .swiftlint.yml

analyse:
	swift build -Xswiftc -warnings-as-errors -Xswiftc -strict-concurrency=complete

docs:
	mkdir -p .out/docs
	swift package --allow-writing-to-directory .out/docs \
		generate-documentation \
		--target PetstoreClient \
		--output-path .out/docs \
		--transform-for-static-hosting \
		--warnings-as-errors

clean:
	swift package clean

# Builds every program under examples/ as an executable target of the package.
examples:
	swift build --product Quickstart

examples-run: examples
	SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL="$(API_BASE_URL)" SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN=placeholder swift run --skip-build Quickstart
