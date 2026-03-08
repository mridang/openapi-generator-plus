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
    /// </summary>
    public string Serialize(object? value)
    {
        return JsonSerializer.Serialize(value, _options);
    }

    /// <summary>
    /// Deserialize a JSON string to an object of the specified type.
    /// </summary>
    public T? Deserialize<T>(string? json)
    {
        if (string.IsNullOrEmpty(json))
        {
            return default;
        }
        return JsonSerializer.Deserialize<T>(json, _options);
    }

    /// <summary>
    /// Convert a value to a string suitable for use as a URL path parameter.
    /// </summary>
    public static string ToPathValue(object? value)
    {
        if (value == null)
        {
            return "";
        }
        if (value is bool b)
        {
            return b ? "true" : "false";
        }
        if (value is DateTimeOffset dto)
        {
            return dto.ToString("o");
        }
        if (value is DateTime dt)
        {
            return dt.ToString("o");
        }
        return value.ToString() ?? "";
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
            var items = new List<string>();
            foreach (var item in list)
            {
                items.Add(item?.ToString() ?? "");
            }
            if (collectionFormat == "multi")
            {
                return items;
            }
            var sep = collectionFormat switch
            {
                "ssv" => " ",
                "tsv" => "\t",
                "pipes" => "|",
                _ => ",",
            };
            return string.Join(sep, items);
        }
        if (value is bool b)
        {
            return b ? "true" : "false";
        }
        if (value is DateTimeOffset dto)
        {
            return dto.ToString("o");
        }
        if (value is DateTime dt)
        {
            return dt.ToString("o");
        }
        return value.ToString() ?? "";
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
            var items = new List<string>();
            foreach (var item in list)
            {
                items.Add(item?.ToString() ?? "");
            }
            return string.Join(",", items);
        }
        if (value is bool b)
        {
            return b ? "true" : "false";
        }
        if (value is DateTimeOffset dto)
        {
            return dto.ToString("o");
        }
        if (value is DateTime dt)
        {
            return dt.ToString("o");
        }
        return value.ToString() ?? "";
    }

    /// <summary>
    /// Convert a value to a representation suitable for use as a form parameter.
    /// </summary>
    public static string ToFormValue(object? value)
    {
        if (value == null)
        {
            return "";
        }
        if (value is bool b)
        {
            return b ? "true" : "false";
        }
        if (value is DateTimeOffset dto)
        {
            return dto.ToString("o");
        }
        if (value is DateTime dt)
        {
            return dt.ToString("o");
        }
        return value.ToString() ?? "";
    }

    private static JsonSerializerOptions CreateDefaultOptions()
    {
        var options = new JsonSerializerOptions
        {
            PropertyNamingPolicy = null,
            DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
            WriteIndented = false,
        };
        options.Converters.Add(new JsonStringEnumConverter());
        return options;
    }
}
