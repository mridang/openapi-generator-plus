# Builds a client from the environment, calls one operation and closes the client.
defmodule Quickstart do
  @moduledoc false

  def main do
    host = System.fetch_env!("SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL")
    token = System.fetch_env!("SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN")
    client = PetstoreClient.Client.with_token(host, token)

    try do
      {:ok, result} =
        PetstoreClient.Api.StoreApi.get_defaults(client.store)

      IO.inspect(result)
    after
      :ok = PetstoreClient.Client.close(client)
    end
  end
end
