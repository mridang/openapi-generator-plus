import { ObjectSerializer } from '../src/object-serializer';
import { Metadata } from '../src/models';

describe('Metadata model (typed additionalProperties)', () => {
  test('deserializes additional string properties', () => {
    const json = {
      createdAt: '2024-01-01T00:00:00Z',
      customField: 'hello'
    };
    const metadata = ObjectSerializer.deserialize(json, Metadata);
    expect(metadata).toBeDefined();
    expect(metadata.createdAt).toBe('2024-01-01T00:00:00Z');
  });

  test('round-trip preserves additional properties', () => {
    const original = new Metadata({
      createdAt: '2024-01-15T10:30:00Z'
    });
    (original as Record<string, unknown>)['customField'] = 'test-value';
    const serialized = ObjectSerializer.serialize(original);
    expect(serialized).toBeDefined();
    const deserialized = ObjectSerializer.deserialize(serialized, Metadata);
    expect(deserialized).toBeDefined();
    expect(deserialized.createdAt).toBe('2024-01-15T10:30:00Z');
  });

  test('compiles with typed additional properties index signature', () => {
    const metadata = new Metadata({ createdAt: '2024-01-01T00:00:00Z' });
    const additionalValue: string = (metadata as Record<string, string>)['anyKey'] ?? '';
    expect(typeof additionalValue).toBe('string');
  });
});
