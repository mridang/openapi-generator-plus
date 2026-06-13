// ignore_for_file: unused_import
import 'dart:typed_data';

/// SetPetPreferencesOptions holds optional parameters for the setPetPreferences operation.
class SetPetPreferencesOptions {
  final String nickname;

  final List<String>? tags;

  final String? note;

  const SetPetPreferencesOptions({
    required this.nickname,
    this.tags,
    this.note,
  });
}
