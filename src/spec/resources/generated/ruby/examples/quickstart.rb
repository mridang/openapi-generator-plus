# frozen_string_literal: true

require 'petstore_client'

host = ENV.fetch('SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL')
token = ENV.fetch('SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN')
client = Petstore::Client::Petstore.with_token(host, token)
begin
  result = client.store.get_defaults
  pp result
ensure
  client.close
end
