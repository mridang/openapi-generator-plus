import Foundation

/// Options for the searchStore operation.
public struct SearchStoreOptions: Sendable {
  /// JSON-encoded query parameter (OAS `content`, not `schema`)
  public let q: PhotoMetadata?
  public let labels: [String]?
  public let at: String?
  public let xFilter: [String: String]?

  public init(
    q: PhotoMetadata? = nil, labels: [String]? = nil, at: String? = nil,
    xFilter: [String: String]? = nil
  ) {
    self.q = q
    self.labels = labels
    self.at = at
    self.xFilter = xFilter
  }
}
