import Foundation

/// Options for the listPetCatalog operation.
public struct ListPetCatalogOptions: Sendable {
  /// The number of pets on a page
  public let limit: Int?
  /// Where to continue, as returned in next_cursor of the previous page
  public let cursor: String?
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
    limit: Int? = nil, cursor: String? = nil, timeout: Int? = nil, headers: [String: String]? = nil,
    maxRetries: Int? = nil
  ) {
    self.limit = limit
    self.cursor = cursor
    self.timeout = timeout
    self.headers = headers
    self.maxRetries = maxRetries
  }

  /// Returns a copy of these options that continues the list at `cursor`: the
  /// same value for every other parameter and the same timeout, headers and
  /// retry count. A paged list uses it to send each page with its own cursor.
  public func withCursor(_ cursor: String) -> ListPetCatalogOptions {
    return ListPetCatalogOptions(
      limit: limit, cursor: cursor, timeout: timeout, headers: headers, maxRetries: maxRetries)
  }
}
