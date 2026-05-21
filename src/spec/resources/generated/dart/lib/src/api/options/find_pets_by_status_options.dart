/// FindPetsByStatusOptions holds optional parameters for the findPetsByStatus operation.
class FindPetsByStatusOptions {
  const FindPetsByStatusOptions({
    this.status,
    this.filter,
  });

  /// Status values that need to be considered for filter
  final String? status;

  /// Filter criteria as key-value pairs
  final Map<String, String>? filter;
}
