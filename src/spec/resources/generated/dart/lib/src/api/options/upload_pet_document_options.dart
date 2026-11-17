import 'dart:typed_data';

import '../../models/swatch.dart';

/// Options for the uploadPetDocument operation.
class UploadPetDocumentOptions {
  final Uint8List file;

  final String? documentType;

  final String? notes;

  final bool? isPrimary;

  final int? pageCount;

  final DateTime? takenAt;

  final Swatch? swatch;

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

  const UploadPetDocumentOptions({
    required this.file,
    this.documentType,
    this.notes,
    this.isPrimary,
    this.pageCount,
    this.takenAt,
    this.swatch,
    this.timeout,
    this.headers,
    this.maxRetries,
  });
}
