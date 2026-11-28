/**
 * Options for the findPetsByStatus operation.
 */
export interface FindPetsByStatusOptions {
  /**
   * Status values that need to be considered for filter
   * @example status: Available only - available
   * @example status: Sold pets - sold
   * @deprecated This parameter is deprecated.
   */
  readonly status?: string;
  /** Filter criteria as key-value pairs */
  readonly filter?: { [key: string]: string };
  /** Only return pets born on or after this date */
  readonly bornAfter?: string | Date;
  readonly n2ndPage?: number;
  /** Reference date for the report */
  readonly reportDate?: string | Date;
}
