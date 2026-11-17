import '../../models/photo_metadata.dart';

/// Options for the searchStore operation.
class SearchStoreOptions {
  /// JSON-encoded query parameter (OAS `content`, not `schema`)
  final PhotoMetadata? q;

  final List<String>? labels;

  final String? at;

  final Map<String, String>? xFilter;

  /// End-to-end timeout in milliseconds for this call only. When non-null it
  /// replaces the transport's timeout; null keeps it. Must not be negative.
  final int? timeout;

  /// Headers sent with this call only. A name that is already set, in any
  /// case, is replaced.
  final Map<String, String>? headers;

  /// How many times this call is sent again after a retryable failure,
  /// replacing the transport's `maxRetries`. 0 disables retries; null keeps
  /// the transport's value.
  final int? maxRetries;

  const SearchStoreOptions({
    this.q,
    this.labels,
    this.at,
    this.xFilter,
    this.timeout,
    this.headers,
    this.maxRetries,
  });
}
