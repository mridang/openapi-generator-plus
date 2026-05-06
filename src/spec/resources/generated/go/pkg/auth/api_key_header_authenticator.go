package auth

type ApiKeyHeaderAuthenticator struct {
	*ApiKeyAuthenticator
}

func NewApiKeyHeaderAuthenticator(host string, apiKey string) *ApiKeyHeaderAuthenticator {
	inner := NewApiKeyAuthenticator(host, "X-API-Key", apiKey, ApiKeyLocationHeader)
	return &ApiKeyHeaderAuthenticator{
		ApiKeyAuthenticator: inner,
	}
}
