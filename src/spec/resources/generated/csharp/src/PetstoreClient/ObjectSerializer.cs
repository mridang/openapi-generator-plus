using System.Collections;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace PetstoreClient;

/// <summary>
/// Handles JSON serialization and deserialization for API requests and responses.
/// </summary>
public class ObjectSerializer
{
    private readonly JsonSerializerOptions _options;

    public ObjectSerializer()
    {
        _options = CreateDefaultOptions();
    }

    public ObjectSerializer(JsonSerializerOptions options)
    {
        _options = options ?? throw new ArgumentNullException(nameof(options));
    }

    /// <summary>
    /// Serialize an object to a JSON string.
    /// Unwraps oneOf/anyOf wrapper objects by serializing their ActualInstance.
    /// </summary>
    public string Serialize(object? value)
    {
        if (value != null)
        {
            System.Reflection.PropertyInfo? actualProp = value
                .GetType()
                .GetProperty("ActualInstance");
            if (actualProp != null)
            {
                value = actualProp.GetValue(value);
            }
        }
        return JsonSerializer.Serialize(value, _options);
    }

    /// <summary>
    /// Deserialize a JSON string to an object of the specified type.
    /// </summary>
    public T? Deserialize<T>(string? json)
    {
        return string.IsNullOrEmpty(json) ? default : JsonSerializer.Deserialize<T>(json, _options);
    }

    /// <summary>
    /// Convert a scalar value to its canonical string representation.
    /// Booleans are lowercased, date/time values use ISO 8601 round-trip format,
    /// and null values return an empty string.
    /// </summary>
    public static string Stringify(object? value)
    {
        return value switch
        {
            null => "",
            bool b => b ? "true" : "false",
            DateTimeOffset dto => dto.ToString("o"),
            DateTime dt => dt.ToString("o"),
            _ => value.ToString() ?? "",
        };
    }

    /// <summary>
    /// Convert a value to a string suitable for use as a URL path parameter.
    /// </summary>
    public static string ToPathValue(object? value)
    {
        return Stringify(value);
    }

    /// <summary>
    /// Convert a value to a representation suitable for use as a query parameter.
    /// </summary>
    public static object? ToQueryValue(object? value, string? collectionFormat)
    {
        if (value == null)
        {
            return null;
        }
        if (value is IList list)
        {
            List<string> items = [];
            foreach (object? item in list)
            {
                items.Add(Stringify(item));
            }
            if (collectionFormat == "multi")
            {
                return items;
            }
            string sep = collectionFormat switch
            {
                "ssv" => " ",
                "tsv" => "\t",
                "pipes" => "|",
                _ => ",",
            };
            return string.Join(sep, items);
        }
        return Stringify(value);
    }

    /// <summary>
    /// Convert a value to a string suitable for use as an HTTP header value.
    /// </summary>
    public static string ToHeaderValue(object? value)
    {
        if (value == null)
        {
            return "";
        }
        if (value is IList list)
        {
            List<string> items = [];
            foreach (object? item in list)
            {
                items.Add(Stringify(item));
            }
            return string.Join(",", items);
        }
        return Stringify(value);
    }

    /// <summary>
    /// Convert a value to a representation suitable for use as a form parameter.
    /// </summary>
    public static string ToFormValue(object? value)
    {
        return Stringify(value);
    }

    private static JsonSerializerOptions CreateDefaultOptions()
    {
        JsonSerializerOptions options = new()
        {
            PropertyNamingPolicy = null,
            DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
            WriteIndented = false,
        };
        options.Converters.Add(new JsonStringEnumConverter());
        return options;
    }
}
