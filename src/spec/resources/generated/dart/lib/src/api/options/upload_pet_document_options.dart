// ignore_for_file: unused_import
import 'dart:typed_data';

/// Options for the uploadPetDocument operation.
class UploadPetDocumentOptions {
  final Uint8List file;

  final String? documentType;

  final String? notes;

  const UploadPetDocumentOptions({
    required this.file,
    this.documentType,
    this.notes,
  });
}
