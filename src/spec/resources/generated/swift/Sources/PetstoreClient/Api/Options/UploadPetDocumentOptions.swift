import Foundation

/// UploadPetDocumentOptions holds optional parameters for the uploadPetDocument operation.
public struct UploadPetDocumentOptions: Sendable {
    public var file: Data
    public var documentType: String?
    public var notes: String?

    public init(file: Data, documentType: String? = nil, notes: String? = nil, ) {
        self.file = file
        self.documentType = documentType
        self.notes = notes
    }
}
