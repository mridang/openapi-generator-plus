/// Options for the setPetPreferences operation.
class SetPetPreferencesOptions {
  final String nickname;

  final List<String>? tags;

  final String? note;

  final DateTime? renewalDate;

  final DateTime? reminder;

  final int? intervalDays;

  final double? ratio;

  final bool? urgent;

  final Duration? snooze;

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

  const SetPetPreferencesOptions({
    required this.nickname,
    this.tags,
    this.note,
    this.renewalDate,
    this.reminder,
    this.intervalDays,
    this.ratio,
    this.urgent,
    this.snooze,
    this.timeout,
    this.headers,
    this.maxRetries,
  });
}
