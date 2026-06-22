// ignore_for_file: unused_import
import 'dart:typed_data';

/// Options for the findPetsByStatus operation.
class FindPetsByStatusOptions {
  /// Status values that need to be considered for filter
  final String? status;

  /// Filter criteria as key-value pairs
  final Map<String, String>? filter;

  const FindPetsByStatusOptions({this.status, this.filter});
}
