import { z } from 'zod';

export const CategorySchema = z
  .object({
    id: z.number().optional(),
    name: z.string().optional()
  })
  .passthrough();

export type Category = z.infer<typeof CategorySchema>;

export function instanceOfCategory(value: object): value is Category {
  return true;
}

export function CategoryFromJSON(json: any): Category {
  return CategoryFromJSONTyped(json, false);
}

export function CategoryFromJSONTyped(json: any, ignoreDiscriminator: boolean): Category {
  if (json == null) {
    return json;
  }
  return CategorySchema.parse(json);
}

export function CategoryToJSON(json: any): Category {
  return CategoryToJSONTyped(json, false);
}

export function CategoryToJSONTyped(value?: Category | null, ignoreDiscriminator: boolean = false): any {
  if (value == null) {
    return value;
  }
  return value;
}
