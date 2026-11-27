import Foundation

/// Options for the setPetPreferences operation.
public struct SetPetPreferencesOptions: Sendable {
  public let nickname: String
  public let tags: [String]?
  public let note: String?
  public let renewalDate: String?
  public let reminder: Date?
  public let intervalDays: Int?
  public let ratio: Double?
  public let urgent: Bool?
  public let snooze: Duration?
  /// Per-call timeout in milliseconds, greater than zero. Replaces the
  /// transport's timeout for this call; nil keeps it.
  public let timeout: Int?
  /// Per-call request headers. Sent after the default and authentication
  /// headers and replacing a header of the same name, whatever its casing.
  public let headers: [String: String]?
  /// Per-call retry limit. Replaces the transport's `maxRetries` for this
  /// call; 0 turns retries off and nil keeps the transport's value.
  public let maxRetries: Int?

  public init(
    nickname: String, tags: [String]? = nil, note: String? = nil, renewalDate: String? = nil,
    reminder: Date? = nil, intervalDays: Int? = nil, ratio: Double? = nil, urgent: Bool? = nil,
    snooze: Duration? = nil, timeout: Int? = nil, headers: [String: String]? = nil,
    maxRetries: Int? = nil
  ) {
    self.nickname = nickname
    self.tags = tags
    self.note = note
    self.renewalDate = renewalDate
    self.reminder = reminder
    self.intervalDays = intervalDays
    self.ratio = ratio
    self.urgent = urgent
    self.snooze = snooze
    self.timeout = timeout
    self.headers = headers
    self.maxRetries = maxRetries
  }
}
