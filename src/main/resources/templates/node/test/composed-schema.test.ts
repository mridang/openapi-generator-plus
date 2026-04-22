import { ObjectSerializer } from '../src/object-serializer';
import { PetWithOwner, PetFood, DryFood, Medication } from '../src/models';
import * as models from '../src/models';

describe('Composed Schema Deserialization', () => {
  test('allOf: PetWithOwner deserializes all properties', () => {
    const data = {
      name: 'doggie',
      photoUrls: ['http://example.com/photo.jpg'],
      ownerName: 'John',
      ownerEmail: 'john@example.com'
    };
    const result = ObjectSerializer.deserialize(data, PetWithOwner);
    expect(result).toBeDefined();
    expect(result.name).toBe('doggie');
    expect(result.ownerName).toBe('John');
    expect(result.ownerEmail).toBe('john@example.com');
  });

  test('oneOf with discriminator: PetFood resolves to DryFood', () => {
    const data = { foodType: 'dry', weightKg: 2.5 };
    const result = ObjectSerializer.deserialize(data, PetFood);
    expect(result).toBeDefined();
    const instance = result.getActualInstance();
    expect(instance).toBeDefined();
    expect(instance).toBeInstanceOf(DryFood);
  });

  test('anyOf: PetTreatment is a constructable class', () => {
    // PetTreatment must be a class (not just a type alias) to support runtime deserialization
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const PetTreatmentCls = (models as any)['PetTreatment'];
    expect(typeof PetTreatmentCls).toBe('function');
  });

  test('anyOf: PetTreatment deserializes Medication', () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const PetTreatmentCls = (models as any)['PetTreatment'];
    expect(PetTreatmentCls).toBeDefined();
    const data = { drugName: 'Amoxicillin', dosage: '500mg' };
    const result = ObjectSerializer.deserialize(data, PetTreatmentCls);
    expect(result).toBeDefined();
    const instance = result.getActualInstance();
    expect(instance).toBeDefined();
    expect(instance).toBeInstanceOf(Medication);
  });
});
