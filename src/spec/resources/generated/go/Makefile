.PHONY: all build test lint format format-check fmt gofumpt vet staticcheck docs clean

all: build

build:
	go build ./...

test:
	mkdir -p .out/reports
	go run gotest.tools/gotestsum --junitfile .out/reports/junit.xml -- -v -race -coverprofile=.out/coverage.out ./...
	go run github.com/boumenot/gocover-cobertura < .out/coverage.out > .out/coverage.xml

lint: format-check vet staticcheck
	golangci-lint run ./...

format-check:
	@test -z "$$(gofmt -s -l .)" || { echo "gofmt -s: these files need formatting:"; gofmt -s -l .; exit 1; }
	@test -z "$$(go tool gofumpt -l .)" || { echo "gofumpt: these files need formatting:"; go tool gofumpt -l .; exit 1; }

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
	go install github.com/princjef/gomarkdoc/cmd/gomarkdoc@v1.1.0
	mkdir -p .out/docs
	gomarkdoc --exclude-dirs ./test/... --output '.out/docs/{{.Dir}}.md' ./...

clean:
	go clean ./...
