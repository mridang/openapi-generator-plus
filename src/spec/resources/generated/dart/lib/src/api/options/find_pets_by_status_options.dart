/// Options for the findPetsByStatus operation.
class FindPetsByStatusOptions {
  /// Status values that need to be considered for filter
  @Deprecated('This parameter is deprecated.')
  final String? status;

  /// Filter criteria as key-value pairs
  final Map<String, String>? filter;

  /// Only return pets born on or after this date
  final String? bornAfter;

  /// Reference date for the report
  final String? reportDate;

  const FindPetsByStatusOptions({
    this.status,
    this.filter,
    this.bornAfter,
    this.reportDate,
  });
}
