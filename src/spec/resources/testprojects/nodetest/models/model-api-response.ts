import { z } from 'zod';

export const ModelApiResponseSchema = z
  .object({
    code: z.number().optional(),
    type: z.string().optional(),
    message: z.string().optional()
  })
  .passthrough();

export type ModelApiResponse = z.infer<typeof ModelApiResponseSchema>;

export function instanceOfModelApiResponse(value: object): value is ModelApiResponse {
  return true;
}

export function ModelApiResponseFromJSON(json: any): ModelApiResponse {
  return ModelApiResponseFromJSONTyped(json, false);
}

export function ModelApiResponseFromJSONTyped(json: any, ignoreDiscriminator: boolean): ModelApiResponse {
  if (json == null) {
    return json;
  }
  return ModelApiResponseSchema.parse(json);
}

export function ModelApiResponseToJSON(json: any): ModelApiResponse {
  return ModelApiResponseToJSONTyped(json, false);
}

export function ModelApiResponseToJSONTyped(
  value?: ModelApiResponse | null,
  ignoreDiscriminator: boolean = false
): any {
  if (value == null) {
    return value;
  }
  return value;
}
