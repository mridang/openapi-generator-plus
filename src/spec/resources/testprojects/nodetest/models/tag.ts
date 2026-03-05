import { z } from 'zod';

export const TagSchema = z
  .object({
    id: z.number().optional(),
    name: z.string().optional()
  })
  .passthrough();

export type Tag = z.infer<typeof TagSchema>;

export function instanceOfTag(value: object): value is Tag {
  return true;
}

export function TagFromJSON(json: any): Tag {
  return TagFromJSONTyped(json, false);
}

export function TagFromJSONTyped(json: any, ignoreDiscriminator: boolean): Tag {
  if (json == null) {
    return json;
  }
  return TagSchema.parse(json);
}

export function TagToJSON(json: any): Tag {
  return TagToJSONTyped(json, false);
}

export function TagToJSONTyped(value?: Tag | null, ignoreDiscriminator: boolean = false): any {
  if (value == null) {
    return value;
  }
  return value;
}
