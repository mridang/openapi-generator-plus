import { Client } from "petstore-client";

async function main(): Promise<void> {
  const host = process.env.SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL;
  if (host === undefined) {
    throw new Error("SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL is not set");
  }
  const token = process.env.SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN;
  if (token === undefined) {
    throw new Error("SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN is not set");
  }
  const client = Client.withToken(host, token);
  try {
    const result = await client.store.getDefaults();
    console.log(result);
  } finally {
    await client.close();
  }
}

void main();
