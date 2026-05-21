import 'package:petstore_client/src/models/photo_metadata.dart';

/// AddPetPhotosOptions holds optional parameters for the addPetPhotos operation.
class AddPetPhotosOptions {
  const AddPetPhotosOptions({
    required this.files,
    required this.metadata,
  });
  final List<List<int>> files;

  final PhotoMetadata metadata;
}
