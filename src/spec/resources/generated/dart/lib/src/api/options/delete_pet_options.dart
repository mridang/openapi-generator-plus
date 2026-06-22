// ignore_for_file: unused_import
import 'dart:typed_data';

import '../../auth/authenticator.dart';

/// Options for the deletePet operation.
class DeletePetOptions {
  /// Session cookie used for authentication
  final String? apiKey;

  final Authenticator? auth;

  const DeletePetOptions({this.apiKey, this.auth});
}
