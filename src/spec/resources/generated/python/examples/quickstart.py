import asyncio
import os

from petstore_client.client import Client


async def main() -> None:
    host = os.environ["SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL"]
    token = os.environ["SWAGGER_PETSTORE_OPEN_API_3_0_ACCESS_TOKEN"]
    with Client.with_token(host, token) as client:
        result = await client.store.export_orders()
        print(result)


asyncio.run(main())
