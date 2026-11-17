import Foundation

/// Options for the deletePet operation.
public struct DeletePetOptions: Sendable {
  /// Session cookie used for authentication
  public let apiKey: String?
  /// Per-call timeout in milliseconds, greater than zero. Replaces the
  /// transport's timeout for this call; nil keeps it.
  public let timeout: Int?
  /// Per-call request headers. Sent after the default and authentication
  /// headers and replacing a header of the same name, whatever its casing.
  public let headers: [String: String]?
  /// Per-call retry limit. Replaces the transport's `maxRetries` for this
  /// call; 0 turns retries off and nil keeps the transport's value.
  public let maxRetries: Int?
  /// Per-operation authenticator. When set, it overrides the client's
  /// configured credentials for this call only; when nil, the configured
  /// credentials are used.
  public let auth: Authenticator?

  public init(
    apiKey: String? = nil, timeout: Int? = nil, headers: [String: String]? = nil,
    maxRetries: Int? = nil, auth: Authenticator? = nil
  ) {
    self.apiKey = apiKey
    self.timeout = timeout
    self.headers = headers
    self.maxRetries = maxRetries
    self.auth = auth
  }
}
