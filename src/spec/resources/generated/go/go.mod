// SPDX-License-Identifier: MIT
module petstore

go 1.25

require (
	github.com/andybalholm/brotli v1.1.1
	github.com/google/uuid v1.6.0
	github.com/klauspost/compress v1.17.4
	github.com/testcontainers/testcontainers-go v0.37.0
)

tool (
	github.com/boumenot/gocover-cobertura
	gotest.tools/gotestsum
	honnef.co/go/tools/cmd/staticcheck
	mvdan.cc/gofumpt
)
