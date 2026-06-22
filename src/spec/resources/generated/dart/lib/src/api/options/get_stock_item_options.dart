// ignore_for_file: unused_import
import 'dart:typed_data';

/// GetStockItemOptions holds optional parameters for the getStockItem operation.
class GetStockItemOptions {
  /// Only consider stock as of this instant
  final DateTime? asOf;

  const GetStockItemOptions({this.asOf});
}
