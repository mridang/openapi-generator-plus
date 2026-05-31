import 'dart:typed_data';

/// UploadPetDocumentOptions holds optional parameters for the uploadPetDocument operation.
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
