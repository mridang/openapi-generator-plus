#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI-like properties should not be strings
#pragma warning disable CA1711 // Identifiers should not have incorrect suffix
#pragma warning disable CA1724 // Type names should not match namespaces
#pragma warning disable CA1819 // Properties should not return arrays
#pragma warning disable CA2227 // Collection properties should be read only
#pragma warning disable CS0618 // Type or member is obsolete
#pragma warning disable CS1591 // Missing XML comment for publicly visible type or member

using System.Text.Json;
using System.Text.Json.Serialization;

namespace PetstoreClient.Models;

/// <summary>
/// A treatment that can match a medication, a surgery, or both
/// </summary>
[JsonConverter(typeof(PetTreatmentConverter))]
public class PetTreatment
{
    public object? ActualInstance { get; set; }

    public PetTreatment(object value)
    {
        ActualInstance = value;
    }

    private class PetTreatmentConverter : JsonConverter<PetTreatment>
    {
        public override PetTreatment? Read(
            ref Utf8JsonReader reader,
            Type typeToConvert,
            JsonSerializerOptions options
        )
        {
            using var doc = JsonDocument.ParseValue(ref reader);
            var raw = doc.RootElement.GetRawText();
            try
            {
                return new PetTreatment(JsonSerializer.Deserialize<Medication>(raw, options)!);
            }
            catch (JsonException)
            { /* try next schema */
            }
            try
            {
                return new PetTreatment(JsonSerializer.Deserialize<Surgery>(raw, options)!);
            }
            catch (JsonException)
            { /* try next schema */
            }
            return new PetTreatment(JsonDocument.Parse(raw).RootElement.Clone());
        }

        public override void Write(
            Utf8JsonWriter writer,
            PetTreatment value,
            JsonSerializerOptions options
        )
        {
            JsonSerializer.Serialize(writer, value.ActualInstance, options);
        }
    }
}
