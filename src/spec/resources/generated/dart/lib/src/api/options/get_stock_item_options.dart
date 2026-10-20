/// Options for the getStockItem operation.
class GetStockItemOptions {
  /// Only consider stock as of this instant
  final DateTime? asOf;

  const GetStockItemOptions({this.asOf});
}
