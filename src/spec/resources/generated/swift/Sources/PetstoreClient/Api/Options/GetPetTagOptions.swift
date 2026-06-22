import Foundation

/// GetPetTagOptions holds optional parameters for the getPetTag operation.
public struct GetPetTagOptions: Sendable {
  public let colors: [String]?
  public let sizes: [String]?
  public let filter: String?

  public init(colors: [String]? = nil, sizes: [String]? = nil, filter: String? = nil) {
    self.colors = colors
    self.sizes = sizes
    self.filter = filter
  }
}
