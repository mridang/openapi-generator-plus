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

  public init(
    file: Data, documentType: String? = nil, notes: String? = nil, isPrimary: Bool? = nil,
    pageCount: Int64? = nil, takenAt: Date? = nil, swatch: Swatch? = nil
  ) {
    self.file = file
    self.documentType = documentType
    self.notes = notes
    self.isPrimary = isPrimary
    self.pageCount = pageCount
    self.takenAt = takenAt
    self.swatch = swatch
  }
}
