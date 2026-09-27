/// Options for the getPetTag operation.
class GetPetTagOptions {
  final List<String>? colors;

  final List<String>? sizes;

  final String? filter;

  /// Query value whose RFC 3986 reserved characters must be sent literally (OAS allowReserved), for example a version expression such as v1.0/beta:rc1 keeping the slash and colon.
  final String? revision;

  const GetPetTagOptions({this.colors, this.sizes, this.filter, this.revision});
}
