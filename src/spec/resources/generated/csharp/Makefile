.PHONY: build test lint format format-dotnet analyze audit docs clean examples examples-run

build:
	dotnet build

test:
	dotnet test --verbosity normal

lint:
	dotnet csharpier check .
	dotnet format --verify-no-changes --severity warn

format:
	dotnet csharpier format .
	dotnet format --severity warn

format-dotnet:
	dotnet format --severity warn

analyze:
	dotnet build --no-restore /warnaserror

audit:
	mkdir -p .out
	dotnet list package --vulnerable --include-transitive | tee .out/audit.txt
	@! grep -q "has the following vulnerable packages" .out/audit.txt

docs:
	dotnet tool restore
	dotnet docfx metadata docfx.json --warningsAsErrors
	dotnet docfx build docfx.json --warningsAsErrors

clean:
	dotnet clean

# examples/Quickstart is a project of its own; building it builds every file under examples.
examples:
	dotnet build examples/Quickstart/Quickstart.csproj

examples-run: examples
	SWAGGER_PETSTORE_OPEN_API_3_0_HOST="$(API_BASE_URL)" SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN=placeholder dotnet run --no-build --project examples/Quickstart/Quickstart.csproj
