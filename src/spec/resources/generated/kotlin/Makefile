.PHONY: build test clean format lint docs

build:
	./gradlew build

test:
	./gradlew test koverXmlReport

clean:
	./gradlew clean

format:
	./gradlew ktlintFormat

lint:
	./gradlew ktlintCheck detekt

docs:
	./gradlew dokkaGenerate
