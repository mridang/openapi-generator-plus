#!/bin/sh
cd /tmp && mkdir -p keywords && cd keywords
dotnet new console --force 1>&2
dotnet add package Microsoft.CodeAnalysis.CSharp 1>&2
cp /scripts/CSharpKeywords.cs Program.cs
dotnet run
