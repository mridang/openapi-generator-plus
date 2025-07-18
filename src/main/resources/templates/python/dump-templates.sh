#!/usr/bin/env bash

docker run --rm \
  --volume=".:/templates" \
  openapitools/openapi-generator-cli:v7.14.0 author template \
    --generator-name=python \
    --output=/templates
