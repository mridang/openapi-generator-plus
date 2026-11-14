import Foundation

/// Options for the setPetPreferences operation.
public struct SetPetPreferencesOptions: Sendable {
  public let nickname: String
  public let tags: [String]?
  public let note: String?
  public let renewalDate: String?
  public let reminder: Date?

  public init(
    nickname: String, tags: [String]? = nil, note: String? = nil, renewalDate: String? = nil,
    reminder: Date? = nil
  ) {
    self.nickname = nickname
    self.tags = tags
    self.note = note
    self.renewalDate = renewalDate
    self.reminder = reminder
  }
}
