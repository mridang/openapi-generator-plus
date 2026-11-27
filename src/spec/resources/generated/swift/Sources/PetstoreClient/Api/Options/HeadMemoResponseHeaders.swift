import Foundation

/// The response headers the headMemo operation declares, read in their
/// declared types. Build it from the headers of the raw result with
/// `HeadMemoResponseHeaders.from(result.headers)`.
public struct HeadMemoResponseHeaders: Sendable {
  /// The revision of the memo
  public let xRevision: Int?

  private init(xRevision: Int?) {
    self.xRevision = xRevision
  }

  /// Reads the declared headers from a raw response header map, matching
  /// each name whatever its casing. An absent header leaves its property nil.
  ///
  /// - Parameter headers: The response headers, as `ApiResult.headers` gives them.
  /// - Returns: The declared headers in their declared types.
  /// - Throws: `SerializationError` when a header value does not parse as its
  ///   declared type.
  public static func from(_ headers: [String: String]) throws -> HeadMemoResponseHeaders {
    return HeadMemoResponseHeaders(
      xRevision: try lookup(headers, "X-Revision").map { raw in
        try parse(raw, "X-Revision", "integer") { Int($0) }
      }
    )
  }

  /// The value of the header `name`, compared without case, or nil.
  private static func lookup(_ headers: [String: String], _ name: String) -> String? {
    return headers.first { $0.key.caseInsensitiveCompare(name) == .orderedSame }?.value
  }

  /// The comma-separated items of a header value, each one trimmed.
  private static func items(_ raw: String) -> [String] {
    return raw.split(separator: ",", omittingEmptySubsequences: false)
      .map { $0.trimmingCharacters(in: .whitespaces) }
  }

  /// Converts one header value, or throws the error that names the header.
  private static func parse<T>(
    _ raw: String, _ name: String, _ kind: String, _ convert: (String) -> T?
  ) throws -> T {
    guard let value = convert(raw) else {
      throw SerializationError(
        message: "Response header '\(name)' is not a valid \(kind): '\(raw)'")
    }
    return value
  }
}
