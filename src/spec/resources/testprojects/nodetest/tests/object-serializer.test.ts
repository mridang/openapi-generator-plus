import { ObjectSerializer } from '../object-serializer';
import { Category } from '../models';

describe('ObjectSerializer', () => {
  describe('toPathValue', () => {
    test('returns empty string for null', () => {
      expect(ObjectSerializer.toPathValue(null)).toBe('');
    });

    test('returns the string for a string value', () => {
      expect(ObjectSerializer.toPathValue('hello')).toBe('hello');
    });

    test('converts integer to string', () => {
      expect(ObjectSerializer.toPathValue(42)).toBe('42');
    });

    test('converts true to "true"', () => {
      expect(ObjectSerializer.toPathValue(true)).toBe('true');
    });

    test('converts false to "false"', () => {
      expect(ObjectSerializer.toPathValue(false)).toBe('false');
    });
  });

  describe('toQueryValue', () => {
    test('returns undefined for null', () => {
      expect(ObjectSerializer.toQueryValue(null)).toBeUndefined();
    });

    test('returns the string for a string value', () => {
      expect(ObjectSerializer.toQueryValue('hello')).toBe('hello');
    });

    test('converts integer to string', () => {
      expect(ObjectSerializer.toQueryValue(42)).toBe('42');
    });

    test('converts true to "true"', () => {
      expect(ObjectSerializer.toQueryValue(true)).toBe('true');
    });

    test('joins array with comma by default', () => {
      expect(ObjectSerializer.toQueryValue(['a', 'b', 'c'])).toBe('a,b,c');
    });

    test('joins array with comma for csv', () => {
      expect(ObjectSerializer.toQueryValue(['a', 'b', 'c'], 'csv')).toBe('a,b,c');
    });

    test('joins array with space for ssv', () => {
      expect(ObjectSerializer.toQueryValue(['a', 'b', 'c'], 'ssv')).toBe('a b c');
    });

    test('joins array with tab for tsv', () => {
      expect(ObjectSerializer.toQueryValue(['a', 'b', 'c'], 'tsv')).toBe('a\tb\tc');
    });

    test('joins array with pipe for pipes', () => {
      expect(ObjectSerializer.toQueryValue(['a', 'b', 'c'], 'pipes')).toBe('a|b|c');
    });

    test('returns array as-is for multi', () => {
      expect(ObjectSerializer.toQueryValue(['a', 'b', 'c'], 'multi')).toEqual(['a', 'b', 'c']);
    });
  });

  describe('toHeaderValue', () => {
    test('returns empty string for null', () => {
      expect(ObjectSerializer.toHeaderValue(null)).toBe('');
    });

    test('returns the string for a string value', () => {
      expect(ObjectSerializer.toHeaderValue('hello')).toBe('hello');
    });

    test('converts integer to string', () => {
      expect(ObjectSerializer.toHeaderValue(42)).toBe('42');
    });

    test('joins array with comma', () => {
      expect(ObjectSerializer.toHeaderValue(['a', 'b', 'c'])).toBe('a,b,c');
    });
  });

  describe('toFormValue', () => {
    test('returns empty string for null', () => {
      expect(ObjectSerializer.toFormValue(null)).toBe('');
    });

    test('returns the string for a string value', () => {
      expect(ObjectSerializer.toFormValue('hello')).toBe('hello');
    });

    test('converts integer to string', () => {
      expect(ObjectSerializer.toFormValue(42)).toBe('42');
    });

    test('converts true to "true"', () => {
      expect(ObjectSerializer.toFormValue(true)).toBe('true');
    });

    test('converts false to "false"', () => {
      expect(ObjectSerializer.toFormValue(false)).toBe('false');
    });
  });

  describe('serialize', () => {
    test('serializes a model to plain object', () => {
      const category = new Category();
      category.id = 1;
      category.name = 'Dogs';
      const result = ObjectSerializer.serialize(category);
      expect(result).toBeDefined();
      expect(result.id).toBe(1);
      expect(result.name).toBe('Dogs');
    });

    test('handles null', () => {
      const result = ObjectSerializer.serialize(null);
      expect(result).toBeUndefined();
    });
  });

  describe('deserialize', () => {
    test('deserializes JSON to typed model', () => {
      const json = { id: 1, name: 'Dogs' };
      const category = ObjectSerializer.deserialize(json, Category);
      expect(category).toBeDefined();
      expect(category.id).toBe(1);
      expect(category.name).toBe('Dogs');
    });
  });
});
