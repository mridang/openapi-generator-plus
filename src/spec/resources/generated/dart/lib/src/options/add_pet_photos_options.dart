/// AddPetPhotosOptions holds optional parameters for the addPetPhotos operation.
class AddPetPhotosOptions {
  final List<List<int>> files;

  final PhotoMetadata metadata;

  const AddPetPhotosOptions({
    required this.files,
    required this.metadata,
  });
}
