import Foundation

/// Options for the findPetsByStatus operation.
public struct FindPetsByStatusOptions: Sendable {
    /// Status values that need to be considered for filter
    @available(*, deprecated, message: "This parameter is deprecated.")
    public let status: String?
    /// Filter criteria as key-value pairs
    public let filter: [String: String]?
    /// Only return pets born on or after this date
    public let bornAfter: String?
    public let n2ndPage: Int?
    /// Reference date for the report
    public let reportDate: String?
    /// Per-call timeout in milliseconds, greater than zero. Replaces the
    /// transport's timeout for this call; nil keeps it.
    public let timeout: Int?
    /// Per-call request headers. Sent after the default and authentication
    /// headers and replacing a header of the same name, whatever its casing.
    public let headers: [String: String]?
    /// Per-call retry limit. Replaces the transport's `maxRetries` for this
    /// call; 0 turns retries off and nil keeps the transport's value.
    public let maxRetries: Int?

    public init(status: String? = nil, filter: [String: String]? = nil, bornAfter: String? = nil, n2ndPage: Int? = nil, reportDate: String? = nil, timeout: Int? = nil, headers: [String: String]? = nil, maxRetries: Int? = nil) {
        self.status = status
        self.filter = filter
        self.bornAfter = bornAfter
        self.n2ndPage = n2ndPage
        self.reportDate = reportDate
        self.timeout = timeout
        self.headers = headers
        self.maxRetries = maxRetries
    }
}
