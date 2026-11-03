import Foundation

/// Options for the getPetTag operation.
public struct GetPetTagOptions: Sendable {
  public let colors: [String]?
  public let sizes: [String]?
  public let filter: String?
  /// Query value whose RFC 3986 reserved characters must be sent literally (OAS allowReserved), for example a version expression such as v1.0/beta:rc1 keeping the slash and colon.
  public let revision: String?

  public init(
    colors: [String]? = nil, sizes: [String]? = nil, filter: String? = nil, revision: String? = nil
  ) {
    self.colors = colors
    self.sizes = sizes
    self.filter = filter
    self.revision = revision
  }
}
