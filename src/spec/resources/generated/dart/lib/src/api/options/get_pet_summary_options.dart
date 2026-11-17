/// Options for the getPetSummary operation.
class GetPetSummaryOptions {
  /// Session token: "a<b>" & it's required
  final String session;

  /// Extra dates to summarise
  final List<DateTime>? days;

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

  const GetPetSummaryOptions({
    required this.session,
    this.days,
    this.timeout,
    this.headers,
    this.maxRetries,
  });
}
