import '../../errors/serialization_exception.dart';

/// The headers the success response of the optionsMemo operation declares,
/// read in their declared types from the raw response headers that a
/// `...WithHttpInfo` call returns in `ApiResult.headers`.
class OptionsMemoResponseHeaders {
  /// The `Allow` response header, or null when the response did not
  /// carry it.
  final String? allow;

  /// Creates the headers from values already parsed.
  const OptionsMemoResponseHeaders({this.allow});

  /// Reads each declared header from [headers] by name, in any case. An absent
  /// header leaves its field null; a value that does not parse as the declared
  /// type raises [SerializationException].
  factory OptionsMemoResponseHeaders.from(Map<String, String> headers) =>
      OptionsMemoResponseHeaders(
        allow: _read(headers, 'Allow', (value) => value),
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
