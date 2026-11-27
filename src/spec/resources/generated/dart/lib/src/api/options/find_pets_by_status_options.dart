/// Options for the findPetsByStatus operation.
class FindPetsByStatusOptions {
  /// Status values that need to be considered for filter
  /// ### `status` — Available only
  /// Show only pets currently in stock
  /// ```json
  /// available
  /// ```
  /// ### `status` — Sold pets
  /// Show pets that have been sold
  /// ```json
  /// sold
  /// ```
  @Deprecated('This parameter is deprecated.')
  final String? status;

  /// Filter criteria as key-value pairs
  final Map<String, String>? filter;

  /// Only return pets born on or after this date
  final DateTime? bornAfter;

  final int? n2ndPage;

  /// Reference date for the report
  final DateTime? reportDate;

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

  const FindPetsByStatusOptions({
    this.status,
    this.filter,
    this.bornAfter,
    this.n2ndPage,
    this.reportDate,
    this.timeout,
    this.headers,
    this.maxRetries,
  });
}
