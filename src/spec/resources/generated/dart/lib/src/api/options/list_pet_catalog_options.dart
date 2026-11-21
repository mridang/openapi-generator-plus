/// Options for the listPetCatalog operation.
class ListPetCatalogOptions {
  /// The number of pets on a page
  final int? limit;

  /// Where to continue, as returned in next_cursor of the previous page
  final String? cursor;

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

  const ListPetCatalogOptions({
    this.limit,
    this.cursor,
    this.timeout,
    this.headers,
    this.maxRetries,
  });
}
