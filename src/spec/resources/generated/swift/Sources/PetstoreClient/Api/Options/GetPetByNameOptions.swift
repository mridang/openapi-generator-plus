import Foundation

/// Options for the getPetByName operation.
public struct GetPetByNameOptions: Sendable {
  public let category: String
  public let xCorrelationId: String

  public init(category: String, xCorrelationId: String) {
    self.category = category
    self.xCorrelationId = xCorrelationId
  }
}
