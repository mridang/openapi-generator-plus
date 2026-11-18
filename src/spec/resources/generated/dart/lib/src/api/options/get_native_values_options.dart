/// Options for the getNativeValues operation.
class GetNativeValuesOptions {
  final String? address4;

  final Duration? delay;

  final double? amount;

  final String? xAddress6;

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

  const GetNativeValuesOptions({
    this.address4,
    this.delay,
    this.amount,
    this.xAddress6,
    this.timeout,
    this.headers,
    this.maxRetries,
  });
}
