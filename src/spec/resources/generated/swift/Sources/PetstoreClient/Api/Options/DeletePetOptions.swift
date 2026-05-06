import Foundation

/// DeletePetOptions holds optional parameters for the deletePet operation.
public struct DeletePetOptions: Sendable {
    /// Session cookie used for authentication
    public var apiKey: String?

    public init(apiKey: String? = nil) {
        self.apiKey = apiKey
    }
}
