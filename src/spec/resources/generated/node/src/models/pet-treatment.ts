import { Medication } from './medication.js';
import { Surgery } from './surgery.js';
/**
 * A treatment that can match a medication, a surgery, or both
 */
export type PetTreatmentType = Medication | Surgery;

export class PetTreatment {
  static readonly ANY_OF_SCHEMAS: string[] = ['Medication', 'Surgery'];

  private actualInstance: PetTreatmentType;

  constructor(instance: PetTreatmentType) {
    this.actualInstance = instance;
  }

  getActualInstance(): PetTreatmentType {
    return this.actualInstance;
  }
}
