#!/usr/bin/env bash
# Runs inside mcr.microsoft.com/dotnet/sdk:10.0-alpine. Mounts: /golden (generated csharp SDK, read-only), /fixtures, /bench.
set -euo pipefail
mkdir -p /work/bench && cd /work
cp -r /golden/src/PetstoreClient PetstoreClient
cp /golden/README.md README.md
cp /bench/Bench.csproj /bench/Program.cs bench/
cd bench
dotnet build -c Release -nologo -v q -p:TreatWarningsAsErrors=false -p:EnforceCodeStyleInBuild=false -p:AnalysisMode=None -p:GenerateDocumentationFile=false >&2
DOTNET_TieredPGO=1 dotnet bin/Release/net10.0/PetstoreClient.Test.dll
