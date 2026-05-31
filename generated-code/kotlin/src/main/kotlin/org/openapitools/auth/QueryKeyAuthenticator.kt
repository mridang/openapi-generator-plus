package org.openapitools.auth

class QueryKeyAuthenticator : ApiKeyAuthenticator {
    constructor(host: String, apiKey: String) : super(host, "api_key", apiKey, ApiKeyLocation.QUERY)
}
