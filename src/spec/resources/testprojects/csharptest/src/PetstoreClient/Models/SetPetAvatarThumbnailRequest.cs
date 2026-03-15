#pragma warning disable CA1002 // Do not expose generic lists
#pragma warning disable CA1056 // URI-like properties should not be strings
#pragma warning disable CA1711 // Identifiers should not have incorrect suffix
#pragma warning disable CA1819 // Properties should not return arrays
#pragma warning disable CA2227 // Collection properties should be read only

namespace PetstoreClient.Models;

public class SetPetAvatarThumbnailRequest(object value)
{
    public object? ActualInstance { get; set; } = value;
}
