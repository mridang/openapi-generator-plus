import Foundation

/// The response headers the listPetFeed operation declares, read in their
/// declared types. Build it from the headers of the raw result with
/// `ListPetFeedResponseHeaders.from(result.headers)`.
public struct ListPetFeedResponseHeaders: Sendable {
  /// The next page as rel="next", when there is one
  public let link: String?

  private init(link: String?) {
    self.link = link
  }

  /// Reads the declared headers from a raw response header map, matching
  /// each name whatever its casing. An absent header leaves its property nil.
  ///
  /// - Parameter headers: The response headers, as `ApiResult.headers` gives them.
  /// - Returns: The declared headers in their declared types.
  /// - Throws: `SerializationError` when a header value does not parse as its
  ///   declared type.
  public static func from(_ headers: [String: String]) throws -> ListPetFeedResponseHeaders {
    return ListPetFeedResponseHeaders(
      link: lookup(headers, "Link")
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
