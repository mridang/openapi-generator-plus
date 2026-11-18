// Runs against the generated csharp SDK. Prints rows:
// lang impl model op ns_per_op bytes_per_op allocs_per_op (one per round; allocs are not available on .NET).
using System.Diagnostics;
using System.Reflection;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.Json.Serialization.Metadata;
using PetstoreClient;
using PetstoreClient.Models;

int rounds = int.Parse(Environment.GetEnvironmentVariable("ROUNDS") ?? "5");
double targetMs = double.Parse(Environment.GetEnvironmentVariable("TARGET_MS") ?? "700");
string[] models = ["small", "medium", "large"];
Dictionary<string, string> text = models.ToDictionary(m => m, m => File.ReadAllText($"/fixtures/{m}.json"));

ObjectSerializer generated = new();
MethodInfo create = typeof(ObjectSerializer).GetMethod("CreateDefaultOptions", BindingFlags.NonPublic | BindingFlags.Static)!;
JsonSerializerOptions sourceGenOptions = (JsonSerializerOptions)create.Invoke(null, null)!;
/* The generated enums keep their converters in private nested classes, which source generation cannot name
   (SYSLIB1220), so the enums fall back to the reflection resolver. */
sourceGenOptions.TypeInfoResolver = JsonTypeInfoResolver.Combine(BenchContext.Default, new DefaultJsonTypeInfoResolver());

Type TypeOf(string m) => m switch { "small" => typeof(Tag), "medium" => typeof(Pet), _ => typeof(List<Pet>) };
object Gen(string m, string s) => m switch
{
    "small" => generated.Deserialize<Tag>(s)!,
    "medium" => generated.Deserialize<Pet>(s)!,
    _ => generated.Deserialize<List<Pet>>(s)!,
};
object Src(string m, string s) => JsonSerializer.Deserialize(s, TypeOf(m), sourceGenOptions)!;

var impls = new (string Name, Func<string, string, object> Decode, Func<string, object, string> Encode)[]
{
    ("generated", Gen, (m, v) => generated.Serialize(v)),
    ("sourcegen", Src, (m, v) => JsonSerializer.Serialize(v, TypeOf(m), sourceGenOptions)),
};

static (double Ns, double Bytes) Measure(Func<object> op, double targetMs)
{
    object? sink = null;
    for (int i = 0; i < 3000; i++) sink = op();
    long n = 1;
    while (true)
    {
        long t0 = Stopwatch.GetTimestamp();
        for (long i = 0; i < n; i++) sink = op();
        double dt = Stopwatch.GetElapsedTime(t0).TotalMilliseconds;
        if (dt > 50) { n = Math.Max(1, (long)(n * targetMs / dt)); break; }
        n *= 4;
    }
    GC.Collect();
    long a0 = GC.GetAllocatedBytesForCurrentThread();
    long s0 = Stopwatch.GetTimestamp();
    for (long i = 0; i < n; i++) sink = op();
    double ns = Stopwatch.GetElapsedTime(s0).TotalNanoseconds / n;
    long a1 = GC.GetAllocatedBytesForCurrentThread();
    GC.KeepAlive(sink);
    return (ns, (double)(a1 - a0) / n);
}

foreach (string m in models)
{
    JsonElement want = JsonDocument.Parse(text[m]).RootElement;
    foreach (var impl in impls)
    {
        string back = impl.Encode(m, impl.Decode(m, text[m]));
        if (JsonDocument.Parse(back).RootElement.GetRawText().Length != JsonDocument.Parse(text[m]).RootElement.GetRawText().Length)
            Console.Error.WriteLine($"MISMATCH {impl.Name} {m}: {back.Length} vs {text[m].Length}");
    }
}

for (int r = 0; r < rounds; r++)
{
    foreach (var impl in impls)
    {
        foreach (string m in models)
        {
            string s = text[m];
            var d = Measure(() => impl.Decode(m, s), targetMs);
            Console.WriteLine($"csharp {impl.Name} {m} decode {d.Ns:F1} {d.Bytes:F0} -");
            object value = impl.Decode(m, s);
            var e = Measure(() => impl.Encode(m, value), targetMs);
            Console.WriteLine($"csharp {impl.Name} {m} encode {e.Ns:F1} {e.Bytes:F0} -");
        }
    }
}

[JsonSerializable(typeof(Tag))]
[JsonSerializable(typeof(Pet))]
[JsonSerializable(typeof(List<Pet>))]
internal sealed partial class BenchContext : JsonSerializerContext;
