import Foundation

/// Options for the uploadPetDocument operation.
public struct UploadPetDocumentOptions: Sendable {
  public let file: Data
  public let documentType: String?
  public let notes: String?
  public let isPrimary: Bool?
  public let pageCount: Int64?
  public let takenAt: Date?
  public let swatch: Swatch?
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
    file: Data, documentType: String? = nil, notes: String? = nil, isPrimary: Bool? = nil,
    pageCount: Int64? = nil, takenAt: Date? = nil, swatch: Swatch? = nil, timeout: Int? = nil,
    headers: [String: String]? = nil, maxRetries: Int? = nil
  ) {
    self.file = file
    self.documentType = documentType
    self.notes = notes
    self.isPrimary = isPrimary
    self.pageCount = pageCount
    self.takenAt = takenAt
    self.swatch = swatch
    self.timeout = timeout
    self.headers = headers
    self.maxRetries = maxRetries
  }
}
