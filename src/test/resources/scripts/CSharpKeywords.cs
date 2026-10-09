using Microsoft.CodeAnalysis.CSharp;

foreach (var kind in Enum.GetValues<SyntaxKind>())
{
    if (SyntaxFacts.IsReservedKeyword(kind))
    {
        Console.WriteLine(SyntaxFacts.GetText(kind));
    }
}
