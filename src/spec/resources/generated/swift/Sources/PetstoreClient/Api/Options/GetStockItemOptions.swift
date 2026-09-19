import Foundation

/// Options for the getStockItem operation.
public struct GetStockItemOptions: Sendable {
  /// Only consider stock as of this instant
  public let asOf: Date?

  public init(asOf: Date? = nil) {
    self.asOf = asOf
  }
}
