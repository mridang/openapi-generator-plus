// ignore_for_file: unused_import
import 'dart:typed_data';

import '../../models/photo_metadata.dart';

/// Options for the addPetPhotos operation.
class AddPetPhotosOptions {
  final List<Uint8List> files;

  final PhotoMetadata metadata;

  const AddPetPhotosOptions({required this.files, required this.metadata});
}
