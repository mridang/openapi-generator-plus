.PHONY: build test clean format lint docs examples examples-run

build:
	gradle build

test:
	gradle test koverXmlReport

clean:
	gradle clean

format:
	gradle ktlintFormat

lint:
	gradle ktlintCheck detekt

docs:
	gradle dokkaGenerate

# src/examples/kotlin is a jvmTest source root, so compileTestKotlinJvm builds every file under examples.
examples:
	gradle compileTestKotlinJvm --console=plain

examples-run: examples
	SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL="$(API_BASE_URL)" SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN=placeholder gradle runQuickstart --console=plain -q
