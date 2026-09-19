.PHONY: build test lint format format-dotnet analyze docs clean

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

docs:
	dotnet tool restore
	dotnet docfx metadata docfx.json --warningsAsErrors
	dotnet docfx build docfx.json --warningsAsErrors

clean:
	dotnet clean
