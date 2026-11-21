import Foundation

/// Options for the getBySwatch operation.
public struct GetBySwatchOptions: Sendable {
  public let querySwatch: Swatch?
  public let preferredSwatch: Swatch?
  /// Per-call timeout in milliseconds, greater than zero. Replaces the
  /// transport's timeout for this call; nil keeps it.
  public let timeout: Int?
  /// Per-call request headers. Sent after the default and authentication
  /// headers and replacing a header of the same name, whatever its casing.
  public let headers: [String: String]?
  /// Per-call retry limit. Replaces the transport's `maxRetries` for this
  /// call; 0 turns retries off and nil keeps the transport's value.
  public let maxRetries: Int?

  public init(
    querySwatch: Swatch? = nil, preferredSwatch: Swatch? = nil, timeout: Int? = nil,
    headers: [String: String]? = nil, maxRetries: Int? = nil
  ) {
    self.querySwatch = querySwatch
    self.preferredSwatch = preferredSwatch
    self.timeout = timeout
    self.headers = headers
    self.maxRetries = maxRetries
  }
}
