module petstore

go 1.26

require (
	github.com/google/uuid v1.6.0
	github.com/testcontainers/testcontainers-go v0.37.0
)

tool (
	github.com/boumenot/gocover-cobertura
	gotest.tools/gotestsum
)
