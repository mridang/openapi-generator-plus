.PHONY: build test format docs clean

build:
	dotnet build

test:
	dotnet test --verbosity normal

format:
	dotnet csharpier .

docs:
	dotnet build /p:DocumentationFile=docs/api.xml

clean:
	dotnet clean
