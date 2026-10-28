import type { PhotoMetadata } from "../../models/index.js";
import type { Temporal } from "temporal-polyfill";

/**
 * Options for the searchStore operation.
 */
export interface SearchStoreOptions {
  /** JSON-encoded query parameter (OAS `content`, not `schema`) */
  readonly q?: PhotoMetadata;
  readonly labels?: Set<string>;
  readonly at?: Temporal.PlainTime;
  readonly xFilter?: { [key: string]: string };
}
