/// DeletePetOptions holds optional parameters for the deletePet operation.
class DeletePetOptions {
  /// Session cookie used for authentication
  final String? apiKey;

  const DeletePetOptions({
    this.apiKey,
  });
}
