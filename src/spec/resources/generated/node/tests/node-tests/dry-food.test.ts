import 'reflect-metadata';
import { DryFood } from '../../src/models/dry-food.js';

describe('DryFood', () => {
  it('requires food type field', () => {
    expect(() => new DryFood({ weightKg: 2.5 } as any)).toThrow();
  });

  it('requires weight kg field', () => {
    expect(() => new DryFood({ foodType: 'dry' } as any)).toThrow();
  });

  it('serializes to json', () => {
    const food = new DryFood({ foodType: 'dry', weightKg: 2.5 });

    const json = JSON.parse(JSON.stringify(food));
    const restored = new DryFood(json);

    expect(restored.foodType).toBe('dry');
    expect(restored.weightKg).toBe(2.5);
  });
});
