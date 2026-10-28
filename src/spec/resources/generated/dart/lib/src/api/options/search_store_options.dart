import '../../models/photo_metadata.dart';

/// Options for the searchStore operation.
class SearchStoreOptions {
  /// JSON-encoded query parameter (OAS `content`, not `schema`)
  final PhotoMetadata? q;

  final List<String>? labels;

  final String? at;

  final Map<String, String>? xFilter;

  const SearchStoreOptions({this.q, this.labels, this.at, this.xFilter});
}
