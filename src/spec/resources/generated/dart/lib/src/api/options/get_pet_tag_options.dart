import 'dart:typed_data';

import '../../models/pet_criteria.dart';

/// Options for the getPetTag operation.
class GetPetTagOptions {
  final List<String>? colors;

  final List<String>? sizes;

  final String? filter;

  /// Query value whose RFC 3986 reserved characters must be sent literally (OAS allowReserved), for example a version expression such as v1.0/beta:rc1 keeping the slash and colon.
  final String? revision;

  /// Object-valued query parameter, form style exploded (the OAS default for query)
  final PetCriteria? criteria;

  final PetCriteria? criteriaCsv;

  final PetCriteria? criteriaDeep;

  /// Opaque bytes, base64 on the wire (OAS format byte)
  final Uint8List? fingerprint;

  final PetCriteria? xCriteria;

  final Uint8List? xFingerprint;

  const GetPetTagOptions({
    this.colors,
    this.sizes,
    this.filter,
    this.revision,
    this.criteria,
    this.criteriaCsv,
    this.criteriaDeep,
    this.fingerprint,
    this.xCriteria,
    this.xFingerprint,
  });
}
