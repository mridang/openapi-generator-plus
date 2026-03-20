.PHONY: build test lint format analyze clean

build:
	mvn compile -q -B

test:
	mvn test -q -B

lint:
	mvn spotbugs:check -q -B

format:
	mvn fmt:format -q -B

analyze:
	mvn compile spotbugs:check -q -B

clean:
	mvn clean -q -B
