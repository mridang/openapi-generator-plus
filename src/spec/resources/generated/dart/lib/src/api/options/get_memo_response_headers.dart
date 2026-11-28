import '../../errors/serialization_exception.dart';

/// The headers the success response of the getMemo operation declares,
/// read in their declared types from the raw response headers that a
/// `...WithHttpInfo` call returns in `ApiResult.headers`.
class GetMemoResponseHeaders {
  /// The revision of the memo
  final int? xRevision;

  /// The labels of the memo
  final List<String>? xLabels;

  /// Whether the memo is archived
  ///
  /// Example: `false`
  final bool? xArchived;

  /// Creates the headers from values already parsed.
  const GetMemoResponseHeaders({this.xRevision, this.xLabels, this.xArchived});

  /// Reads each declared header from [headers] by name, in any case. An absent
  /// header leaves its field null; a value that does not parse as the declared
  /// type raises [SerializationException].
  factory GetMemoResponseHeaders.from(Map<String, String> headers) =>
      GetMemoResponseHeaders(
        xRevision: _read(
          headers,
          'X-Revision',
          (value) => _integer('X-Revision', value),
        ),
        xLabels: _read(headers, 'X-Labels', _split),
        xArchived: _read(
          headers,
          'X-Archived',
          (value) => _boolean('X-Archived', value),
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

  /// The items of a list header: the value split on commas, each item trimmed.
  static List<String> _split(String value) =>
      value.split(',').map((item) => item.trim()).toList();

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

  /// [value] read as a boolean: exactly `true` or `false`.
  static bool _boolean(String name, String value) {
    if (value == 'true') {
      return true;
    }
    if (value == 'false') {
      return false;
    }
    throw SerializationException(
      "Response header '$name' is not a valid boolean: '$value'",
    );
  }
}
