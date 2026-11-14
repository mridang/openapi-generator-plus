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

  const UploadPetDocumentOptions({
    required this.file,
    this.documentType,
    this.notes,
    this.isPrimary,
    this.pageCount,
    this.takenAt,
    this.swatch,
  });
}
