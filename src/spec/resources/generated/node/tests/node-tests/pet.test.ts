import 'reflect-metadata';
import { Pet, PetStatusEnum } from '../../src/models/pet.js';

describe('Pet', () => {
  it('requires name field', () => {
    expect(() => new Pet({ photoUrls: new Set(['http://photo.jpg']) } as any)).toThrow();
  });

  it('requires photo urls field', () => {
    expect(() => new Pet({ name: 'doggie' } as any)).toThrow();
  });

  it('rejects invalid status enum', () => {
    const validValues = Object.values(PetStatusEnum);
    expect(validValues).not.toContain('invalid');
    // The model should reject invalid enum values during deserialization
    const pet = new Pet({ name: 'doggie', photoUrls: new Set(), status: 'invalid' } as any);
    expect(validValues).toContain(pet.status);
  });

  it('serializes to json', () => {
    const pet = new Pet({
      id: 1,
      name: 'doggie',
      photoUrls: new Set(['http://photo.jpg']),
      status: PetStatusEnum.Available
    });

    const json = JSON.parse(JSON.stringify(pet));
    const restored = new Pet(json);

    expect(restored.name).toBe('doggie');
    expect(restored.id).toBe(1);
  });
});
