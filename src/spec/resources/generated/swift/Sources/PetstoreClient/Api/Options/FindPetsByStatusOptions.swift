import Foundation

/// FindPetsByStatusOptions holds optional parameters for the findPetsByStatus operation.
public struct FindPetsByStatusOptions: Sendable {
  /// Status values that need to be considered for filter
  public let status: String?
  /// Filter criteria as key-value pairs
  public let filter: [String: String]?

  public init(status: String? = nil, filter: [String: String]? = nil) {
    self.status = status
    self.filter = filter
  }
}
