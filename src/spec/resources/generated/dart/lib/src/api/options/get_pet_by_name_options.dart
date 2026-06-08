import 'dart:typed_data';

/// GetPetByNameOptions holds optional parameters for the getPetByName operation.
class GetPetByNameOptions {
  final String category;

  const GetPetByNameOptions({
    required this.category,
  });
}
