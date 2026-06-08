import 'dart:typed_data';

import '../../auth/authenticator.dart';

/// AddPetTreatmentOptions holds optional parameters for the addPetTreatment operation.
class AddPetTreatmentOptions {
  final Authenticator? auth;

  const AddPetTreatmentOptions({
    this.auth,
  });
}
