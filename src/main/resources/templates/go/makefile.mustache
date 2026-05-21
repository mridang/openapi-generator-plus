.PHONY: all build test lint format fmt gofumpt vet staticcheck docs clean

all: build

build:
	go build ./...

test:
	mkdir -p .out/reports
	go run gotest.tools/gotestsum --junitfile .out/reports/junit.xml -- -v -race -coverprofile=.out/coverage.out ./...
	go run github.com/boumenot/gocover-cobertura < .out/coverage.out > .out/coverage.xml

lint:
	golangci-lint run ./...

format: fmt gofumpt

fmt:
	gofmt -s -w .

gofumpt:
	go tool gofumpt -w .

vet:
	go vet ./...

staticcheck:
	go tool staticcheck ./...

docs:
	go doc ./...

clean:
	go clean ./...
