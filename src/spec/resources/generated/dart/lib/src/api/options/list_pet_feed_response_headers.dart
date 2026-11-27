import '../../errors/serialization_exception.dart';

/// The headers the success response of the listPetFeed operation declares,
/// read in their declared types from the raw response headers that a
/// `...WithHttpInfo` call returns in `ApiResult.headers`.
class ListPetFeedResponseHeaders {
  /// The next page as rel="next", when there is one
  final String? link;

  /// Creates the headers from values already parsed.
  const ListPetFeedResponseHeaders({this.link});

  /// Reads each declared header from [headers] by name, in any case. An absent
  /// header leaves its field null; a value that does not parse as the declared
  /// type raises [SerializationException].
  factory ListPetFeedResponseHeaders.from(Map<String, String> headers) =>
      ListPetFeedResponseHeaders(
        link: _read(headers, 'Link', (value) => value),
      );

  /// The value of the header [name] in [headers], compared without case, run
  /// through [parse]; null when the header is absent.
  static T? _read<T>(
    Map<String, String> headers,
    String name,
    T Function(String value) parse,
  ) {
    final wanted = name.toLowerCase();
    for (final entry in headers.entries) {
      if (entry.key.toLowerCase() == wanted) {
        return parse(entry.value);
      }
    }
    return null;
  }
}
