/// GetPetTagOptions holds optional parameters for the getPetTag operation.
class GetPetTagOptions {
  const GetPetTagOptions({
    this.colors,
    this.sizes,
    this.filter,
  });
  final List<String>? colors;

  final List<String>? sizes;

  final String? filter;
}
