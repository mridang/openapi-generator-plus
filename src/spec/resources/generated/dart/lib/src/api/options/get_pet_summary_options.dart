/// Options for the getPetSummary operation.
class GetPetSummaryOptions {
  /// Session token: "a<b>" & it's required
  final String session;

  /// Extra dates to summarise
  final List<DateTime>? days;

  const GetPetSummaryOptions({required this.session, this.days});
}
