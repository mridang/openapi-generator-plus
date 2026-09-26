import '../../models/swatch.dart';

/// Options for the getBySwatch operation.
class GetBySwatchOptions {
  final Swatch? querySwatch;

  final Swatch? preferredSwatch;

  const GetBySwatchOptions({this.querySwatch, this.preferredSwatch});
}
