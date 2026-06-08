import Foundation

/// DeletePetOptions holds optional parameters for the deletePet operation.
public struct DeletePetOptions: Sendable {
    /// Session cookie used for authentication
    public var apiKey: String?
    /// Per-operation authenticator. When set, it overrides the client's
    /// configured credentials for this call only; when nil, the configured
    /// credentials are used.
    public var auth: Authenticator?

    public init(apiKey: String? = nil, auth: Authenticator? = nil) {
        self.apiKey = apiKey
        self.auth = auth
    }
}
