#nullable enable
using PetstoreClient.Auth;

namespace PetstoreClient.Api.Options;

/// <summary>
/// Options for the DeletePet operation.
/// </summary>
public sealed class DeletePetOptions
{
    /// <summary>Session cookie used for authentication</summary>
    public string? ApiKey { get; init; }

    /// <summary>
    /// End-to-end timeout in milliseconds for this call only: it replaces
    /// <see cref="TransportOptions.Timeout"/> and must be greater than zero.
    /// When null the transport's timeout applies.
    /// </summary>
    public int? Timeout { get; init; }

    /// <summary>
    /// Extra request headers for this call only. They are sent after the
    /// default and authentication headers and replace a header of the same
    /// name, whatever its casing.
    /// </summary>
    public IReadOnlyDictionary<string, string>? Headers { get; init; }

    /// <summary>
    /// Maximum number of retries for this call only: it replaces
    /// <see cref="TransportOptions.MaxRetries"/>, and 0 turns retries off.
    /// When null the transport's setting applies.
    /// </summary>
    public int? MaxRetries { get; init; }

    /// <summary>
    /// Per-operation authenticator. When set, it overrides the client's
    /// configured credentials for this call only; when null the configured
    /// credentials are used.
    /// </summary>
    public IAuthenticator? Auth { get; init; }
}
