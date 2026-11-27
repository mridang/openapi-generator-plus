import '../../errors/serialization_exception.dart';

/// The headers the success response of the headMemo operation declares,
/// read in their declared types from the raw response headers that a
/// `...WithHttpInfo` call returns in `ApiResult.headers`.
class HeadMemoResponseHeaders {
  /// The revision of the memo
  final int? xRevision;

  /// Creates the headers from values already parsed.
  const HeadMemoResponseHeaders({this.xRevision});

  /// Reads each declared header from [headers] by name, in any case. An absent
  /// header leaves its field null; a value that does not parse as the declared
  /// type raises [SerializationException].
  factory HeadMemoResponseHeaders.from(Map<String, String> headers) =>
      HeadMemoResponseHeaders(
        xRevision: _read(
          headers,
          'X-Revision',
          (value) => _integer('X-Revision', value),
        ),
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

  /// [value] read as a base-10 integer with an optional sign; [kind] names the
  /// declared type in the error.
  static int _integer(String name, String value, [String kind = 'integer']) {
    final parsed = RegExp(r'^[+-]?[0-9]+$').hasMatch(value)
        ? int.tryParse(value, radix: 10)
        : null;
    if (parsed == null) {
      throw SerializationException(
        "Response header '$name' is not a valid $kind: '$value'",
      );
    }
    return parsed;
  }
}
