import Foundation

/// Options for the getPetByName operation.
public struct GetPetByNameOptions: Sendable {
  public let category: String

  public init(category: String) {
    self.category = category
  }
}
