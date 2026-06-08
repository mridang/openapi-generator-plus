import Foundation

/// AddPetOptions holds optional parameters for the addPet operation.
public struct AddPetOptions: Sendable {
    /// Per-operation authenticator. When set, it overrides the client's
    /// configured credentials for this call only; when nil, the configured
    /// credentials are used.
    public var auth: Authenticator?

    public init(auth: Authenticator? = nil) {
        self.auth = auth
    }
}
