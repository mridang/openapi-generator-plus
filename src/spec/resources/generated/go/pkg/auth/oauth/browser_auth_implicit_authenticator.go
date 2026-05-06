package oauth

type BrowserAuthImplicitAuthenticator struct {
	*OAuth2ImplicitAuthenticator
}

func NewBrowserAuthImplicitAuthenticator(host string, clientId string) *BrowserAuthImplicitAuthenticator {
	inner := NewOAuth2ImplicitAuthenticator(host, clientId, "https://auth.example.com/authorize", nil)
	return &BrowserAuthImplicitAuthenticator{
		OAuth2ImplicitAuthenticator: inner,
	}
}
