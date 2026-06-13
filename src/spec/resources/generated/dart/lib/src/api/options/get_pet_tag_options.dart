// ignore_for_file: unused_import
import 'dart:typed_data';

/// GetPetTagOptions holds optional parameters for the getPetTag operation.
class GetPetTagOptions {
  final List<String>? colors;

  final List<String>? sizes;

  final String? filter;

  const GetPetTagOptions({this.colors, this.sizes, this.filter});
}
