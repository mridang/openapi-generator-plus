.PHONY: all build test lint fmt vet docs clean

all: build

build:
	go build ./...

test:
	mkdir -p .out/reports
	go run gotest.tools/gotestsum --junitfile .out/reports/junit.xml -- -v -race -coverprofile=.out/coverage.out ./...
	go run github.com/boumenot/gocover-cobertura < .out/coverage.out > .out/coverage.xml

lint:
	golangci-lint run ./...

fmt:
	gofmt -s -w .

vet:
	go vet ./...

docs:
	go doc ./...

clean:
	go clean ./...
