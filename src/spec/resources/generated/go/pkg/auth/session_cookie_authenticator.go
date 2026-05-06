package auth

type SessionCookieAuthenticator struct {
	*ApiKeyAuthenticator
}

func NewSessionCookieAuthenticator(host string, apiKey string) *SessionCookieAuthenticator {
	inner := NewApiKeyAuthenticator(host, "SESSION_ID", apiKey, ApiKeyLocationCookie)
	return &SessionCookieAuthenticator{
		ApiKeyAuthenticator: inner,
	}
}
