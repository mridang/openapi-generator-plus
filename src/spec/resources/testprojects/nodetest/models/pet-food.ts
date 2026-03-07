import { Expose, Type } from 'class-transformer';

/**
 * Food for pets, discriminated by foodType
 */
export type PetFoodType = DryFood | WetFood;

export class PetFood {
  static readonly ONE_OF_SCHEMAS: string[] = ['DryFood', 'WetFood'];
  static readonly DISCRIMINATOR_PROPERTY = 'foodType';
  static readonly DISCRIMINATOR_MAPPING: Record<string, string> = {
    dry: 'DryFood',
    wet: 'WetFood'
  };

  private actualInstance: PetFoodType;

  constructor(instance: PetFoodType) {
    this.actualInstance = instance;
  }

  getActualInstance(): PetFoodType {
    return this.actualInstance;
  }
}
