import Foundation

/// AddPetTreatmentOptions holds optional parameters for the addPetTreatment operation.
public struct AddPetTreatmentOptions: Sendable {
  /// Per-operation authenticator. When set, it overrides the client's
  /// configured credentials for this call only; when nil, the configured
  /// credentials are used.
  public let auth: Authenticator?

  public init(auth: Authenticator? = nil) {
    self.auth = auth
  }
}
