import Foundation

/// GetBySwatchOptions holds optional parameters for the getBySwatch operation.
public struct GetBySwatchOptions: Sendable {
  public let querySwatch: Swatch?
  public let preferredSwatch: Swatch?

  public init(querySwatch: Swatch? = nil, preferredSwatch: Swatch? = nil) {
    self.querySwatch = querySwatch
    self.preferredSwatch = preferredSwatch
  }
}
