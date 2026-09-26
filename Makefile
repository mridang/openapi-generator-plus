MVN := devbox run -- mvn
FAIL_FLAGS := -DfailIfNoTests=false -pl .

.PHONY: generate format test \
        test\:java test\:csharp test\:node test\:python test\:ruby test\:php

## Generate all clients
generate:
	$(MVN) test -Dtest=GenerateClientsTest

## Apply spotless formatting
format:
	$(MVN) spotless:apply

## Run all integration specs
test:
	$(MVN) verify

## Java specs
test\:java:
	$(MVN) verify -Dit.test=JavaBuildSpec,JavaStaticAnalysisSpec,JavaFormattingSpec,JavaClientSpec $(FAIL_FLAGS)

## C# specs
test\:csharp:
	$(MVN) verify -Dit.test=CSharpBuildSpec,CSharpStaticAnalysisSpec,CSharpFormattingSpec,CSharpClientSpec $(FAIL_FLAGS)

## Node specs
test\:node:
	$(MVN) verify -Dit.test=NodeLintingSpec,NodeTypeCheckSpec,NodeFormattingSpec,NodeClientSpec $(FAIL_FLAGS)

## Python specs
test\:python:
	$(MVN) verify -Dit.test=PythonTypeCheckSpec,PythonFormattingSpec,PythonClientSpec $(FAIL_FLAGS)

## Ruby specs
test\:ruby:
	$(MVN) verify -Dit.test=RubyLintingSpec,RubyTypeCheckSpec,RubyFormattingSpec,RubyClientSpec $(FAIL_FLAGS)

## PHP specs
test\:php:
	$(MVN) verify -Dit.test=PhpStaticAnalysisSpec,PhpModernizationSpec,PhpFormattingSpec,PhpClientSpec $(FAIL_FLAGS)
