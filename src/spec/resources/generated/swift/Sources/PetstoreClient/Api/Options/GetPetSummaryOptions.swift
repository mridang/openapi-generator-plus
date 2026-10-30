import Foundation

/// Options for the getPetSummary operation.
public struct GetPetSummaryOptions: Sendable {
  /// Session token: \"a<b>\" & it's required
  public let session: String
  /// Extra dates to summarise
  public let days: [String]?

  public init(session: String, days: [String]? = nil) {
    self.session = session
    self.days = days
  }
}
