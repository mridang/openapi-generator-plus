#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI-like properties should not be strings
#pragma warning disable CA1711 // Identifiers should not have incorrect suffix
#pragma warning disable CA1724 // Type names should not match namespaces
#pragma warning disable CA1819 // Properties should not return arrays
#pragma warning disable CA2227 // Collection properties should be read only
#pragma warning disable CS0618 // Type or member is obsolete
#pragma warning disable CS1591 // Missing XML comment for publicly visible type or member

namespace PetstoreClient.Models;

/// <summary>
/// A treatment that can match a medication, a surgery, or both
/// </summary>
public class PetTreatment
{
    public object? ActualInstance { get; set; }

    public PetTreatment(object value)
    {
        ActualInstance = value;
    }
}
