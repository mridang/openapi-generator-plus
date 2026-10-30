import type { Date } from "../../models/index.js";

/**
 * Options for the getPetSummary operation.
 */
export interface GetPetSummaryOptions {
  /** Session token: \"a<b>\" & it's required */
  readonly session: string;
  /** Extra dates to summarise */
  readonly days?: Array<Date>;
}
