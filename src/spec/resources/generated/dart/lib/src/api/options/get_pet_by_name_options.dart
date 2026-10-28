/// Options for the getPetByName operation.
class GetPetByNameOptions {
  final String category;

  final String xCorrelationId;

  const GetPetByNameOptions({
    required this.category,
    required this.xCorrelationId,
  });
}
