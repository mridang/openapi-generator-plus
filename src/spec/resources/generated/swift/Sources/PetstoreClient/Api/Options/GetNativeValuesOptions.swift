import Foundation

/// Options for the getNativeValues operation.
public struct GetNativeValuesOptions: Sendable {
  public let address4: String?
  public let delay: Duration?
  public let amount: Decimal?
  public let xAddress6: String?
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
    address4: String? = nil, delay: Duration? = nil, amount: Decimal? = nil,
    xAddress6: String? = nil, timeout: Int? = nil, headers: [String: String]? = nil,
    maxRetries: Int? = nil
  ) {
    self.address4 = address4
    self.delay = delay
    self.amount = amount
    self.xAddress6 = xAddress6
    self.timeout = timeout
    self.headers = headers
    self.maxRetries = maxRetries
  }
}
