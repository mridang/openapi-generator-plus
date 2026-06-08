import Foundation

/// UploadPetCertificateOptions holds optional parameters for the uploadPetCertificate operation.
public struct UploadPetCertificateOptions: Sendable {
    public let file: Data

    public init(file: Data, ) {
        self.file = file
    }
}
