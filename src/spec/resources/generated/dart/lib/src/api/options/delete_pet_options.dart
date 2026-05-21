/// DeletePetOptions holds optional parameters for the deletePet operation.
class DeletePetOptions {
  const DeletePetOptions({
    this.apiKey,
  });

  /// Session cookie used for authentication
  final String? apiKey;
}
