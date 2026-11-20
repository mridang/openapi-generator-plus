import Foundation

/// Options for the searchStore operation.
public struct SearchStoreOptions: Sendable {
    /// JSON-encoded query parameter (OAS `content`, not `schema`)
    public let q: PhotoMetadata?
    public let labels: [String]?
    public let at: String?
    public let xFilter: [String: String]?
    /// Per-call timeout in milliseconds, greater than zero. Replaces the
    /// transport's timeout for this call; nil keeps it.
    public let timeout: Int?
    /// Per-call request headers. Sent after the default and authentication
    /// headers and replacing a header of the same name, whatever its casing.
    public let headers: [String: String]?
    /// Per-call retry limit. Replaces the transport's `maxRetries` for this
    /// call; 0 turns retries off and nil keeps the transport's value.
    public let maxRetries: Int?

    public init(q: PhotoMetadata? = nil, labels: [String]? = nil, at: String? = nil, xFilter: [String: String]? = nil, timeout: Int? = nil, headers: [String: String]? = nil, maxRetries: Int? = nil) {
        self.q = q
        self.labels = labels
        self.at = at
        self.xFilter = xFilter
        self.timeout = timeout
        self.headers = headers
        self.maxRetries = maxRetries
    }
}
