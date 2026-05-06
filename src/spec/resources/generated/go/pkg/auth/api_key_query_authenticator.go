package auth

type ApiKeyQueryAuthenticator struct {
	*ApiKeyAuthenticator
}

func NewApiKeyQueryAuthenticator(host string, apiKey string) *ApiKeyQueryAuthenticator {
	inner := NewApiKeyAuthenticator(host, "api_key", apiKey, ApiKeyLocationQuery)
	return &ApiKeyQueryAuthenticator{
		ApiKeyAuthenticator: inner,
	}
}
