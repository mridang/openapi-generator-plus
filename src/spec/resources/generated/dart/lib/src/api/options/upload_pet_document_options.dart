/// UploadPetDocumentOptions holds optional parameters for the uploadPetDocument operation.
class UploadPetDocumentOptions {
  const UploadPetDocumentOptions({
    required this.file,
    this.documentType,
    this.notes,
  });
  final List<int> file;

  final String? documentType;

  final String? notes;
}
