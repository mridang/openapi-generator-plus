import Foundation

/// UploadPetDocumentOptions holds optional parameters for the uploadPetDocument operation.
public struct UploadPetDocumentOptions: Sendable {
  public let file: Data
  public let documentType: String?
  public let notes: String?

  public init(file: Data, documentType: String? = nil, notes: String? = nil) {
    self.file = file
    self.documentType = documentType
    self.notes = notes
  }
}
