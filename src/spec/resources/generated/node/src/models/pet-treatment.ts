import { Medication } from './medication.js';
import { Surgery } from './surgery.js';
/**
 * A treatment that can match a medication, a surgery, or both
 */
export type PetTreatment = Medication | Surgery;
