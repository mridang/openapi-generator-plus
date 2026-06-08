import 'dart:typed_data';

import '../../auth/authenticator.dart';

/// AddPetOptions holds optional parameters for the addPet operation.
class AddPetOptions {
  final Authenticator? auth;

  const AddPetOptions({
    this.auth,
  });
}
