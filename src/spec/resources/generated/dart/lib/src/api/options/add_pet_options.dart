// ignore_for_file: unused_import
import 'dart:typed_data';

import '../../auth/authenticator.dart';

/// Options for the addPet operation.
class AddPetOptions {
  /// Per-operation authenticator. When non-null it overrides the client's
  /// configured credentials for this call only; null falls back to the
  /// configured authenticator.
  final Authenticator? auth;

  const AddPetOptions({this.auth});
}
