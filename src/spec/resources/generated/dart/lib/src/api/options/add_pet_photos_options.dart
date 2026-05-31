import '../../models/photo_metadata.dart';

/// AddPetPhotosOptions holds optional parameters for the addPetPhotos operation.
class AddPetPhotosOptions {
  final List<Uint8List> files;

  final PhotoMetadata metadata;

  const AddPetPhotosOptions({
    required this.files,
    required this.metadata,
  });
}
