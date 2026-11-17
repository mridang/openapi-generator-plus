import Foundation

/// Options for the getPetSummary operation.
public struct GetPetSummaryOptions: Sendable {
  /// Session token: \"a<b>\" & it's required
  public let session: String
  /// Extra dates to summarise
  public let days: [String]?
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
    session: String, days: [String]? = nil, timeout: Int? = nil, headers: [String: String]? = nil,
    maxRetries: Int? = nil
  ) {
    self.session = session
    self.days = days
    self.timeout = timeout
    self.headers = headers
    self.maxRetries = maxRetries
  }
}
