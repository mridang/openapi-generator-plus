using System.Collections;

namespace PetstoreClient;

/// <summary>
/// Serializes parameter values for HTTP requests based on their location.
/// </summary>
public static class ValueSerializer
{
    /// <summary>
    /// Serializes a value for use in an HTTP request parameter based on its location,
    /// schema type, and optional collection format.
    /// </summary>
    /// <param name="value">The value to serialize.</param>
    /// <param name="location">The parameter location (e.g. "query", "path", "header").</param>
    /// <param name="schemaType">The OpenAPI schema type of the parameter.</param>
    /// <param name="collectionFormat">
    /// The collection format for array parameters (e.g. "multi", "csv", "ssv", "tsv", "pipes").
    /// Defaults to <c>null</c>, which is treated as "csv".
    /// </param>
    /// <returns>
    /// The serialized value suitable for the given location, or <c>null</c> for query
    /// parameters with a null input value.
    /// </returns>
#pragma warning disable IDE0060 // Remove unused parameter
    public static object? Serialize(
        object? value,
        string location,
        string schemaType,
        string? collectionFormat = null
    )
#pragma warning restore IDE0060 // Remove unused parameter
    {
        if (value == null)
        {
            return location == "query" ? null : "";
        }

        if (value is IList list)
        {
            if (location == "query")
            {
                return collectionFormat switch
                {
                    "multi" => list.Cast<object>().Select(Stringify).ToList(),
                    "ssv" => string.Join(" ", list.Cast<object>().Select(Stringify)),
                    "tsv" => string.Join("\t", list.Cast<object>().Select(Stringify)),
                    "pipes" => string.Join("|", list.Cast<object>().Select(Stringify)),
                    _ => string.Join(",", list.Cast<object>().Select(Stringify)),
                };
            }

            if (location == "header")
            {
                return string.Join(",", list.Cast<object>().Select(Stringify));
            }
        }

        string str = Stringify(value);

        return location == "path" ? Uri.EscapeDataString(str) : str;
    }

    /// <summary>
    /// Converts a scalar value to its string representation suitable for HTTP parameter use.
    /// </summary>
    /// <param name="value">The value to convert.</param>
    /// <returns>
    /// A string representation of the value. Booleans are lowercased, date/time values use
    /// ISO 8601 round-trip format, and <c>null</c> values return an empty string.
    /// </returns>
    private static string Stringify(object? value)
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
}
