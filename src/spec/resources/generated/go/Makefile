.PHONY: all build test lint fmt vet docs clean

all: build

build:
	go build ./...

test:
	go test -v -race ./...

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
