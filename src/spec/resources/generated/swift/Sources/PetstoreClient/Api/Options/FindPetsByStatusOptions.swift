import Foundation

/// Options for the findPetsByStatus operation.
public struct FindPetsByStatusOptions: Sendable {
  /// Status values that need to be considered for filter
  @available(*, deprecated, message: "This parameter is deprecated.")
  public let status: String?
  /// Filter criteria as key-value pairs
  public let filter: [String: String]?
  /// Only return pets born on or after this date
  public let bornAfter: String?
  /// Reference date for the report
  public let reportDate: String?

  public init(
    status: String? = nil, filter: [String: String]? = nil, bornAfter: String? = nil,
    reportDate: String? = nil
  ) {
    self.status = status
    self.filter = filter
    self.bornAfter = bornAfter
    self.reportDate = reportDate
  }
}
