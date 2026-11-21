import Foundation

/// Options for the getPetTag operation.
public struct GetPetTagOptions: Sendable {
  public let colors: [String]?
  public let sizes: [String]?
  public let filter: String?
  /// Query value whose RFC 3986 reserved characters must be sent literally (OAS allowReserved), for example a version expression such as v1.0/beta:rc1 keeping the slash and colon.
  public let revision: String?
  /// Object-valued query parameter, form style exploded (the OAS default for query)
  public let criteria: PetCriteria?
  public let criteriaCsv: PetCriteria?
  public let criteriaDeep: PetCriteria?
  /// Opaque bytes, base64 on the wire (OAS format byte)
  public let fingerprint: Data?
  public let xCriteria: PetCriteria?
  public let xFingerprint: Data?
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
    colors: [String]? = nil, sizes: [String]? = nil, filter: String? = nil, revision: String? = nil,
    criteria: PetCriteria? = nil, criteriaCsv: PetCriteria? = nil, criteriaDeep: PetCriteria? = nil,
    fingerprint: Data? = nil, xCriteria: PetCriteria? = nil, xFingerprint: Data? = nil,
    timeout: Int? = nil, headers: [String: String]? = nil, maxRetries: Int? = nil
  ) {
    self.colors = colors
    self.sizes = sizes
    self.filter = filter
    self.revision = revision
    self.criteria = criteria
    self.criteriaCsv = criteriaCsv
    self.criteriaDeep = criteriaDeep
    self.fingerprint = fingerprint
    self.xCriteria = xCriteria
    self.xFingerprint = xFingerprint
    self.timeout = timeout
    self.headers = headers
    self.maxRetries = maxRetries
  }
}
