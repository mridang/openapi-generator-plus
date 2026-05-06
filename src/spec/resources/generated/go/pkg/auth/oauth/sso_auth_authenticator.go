package oauth

type SsoAuthAuthenticator struct {
	*OpenIdConnectAuthenticator
}

func NewSsoAuthAuthenticator(host string, clientId string, clientSecret string, redirectUri string) *SsoAuthAuthenticator {
	inner := NewOpenIdConnectAuthenticator(host, "https://auth.example.com/.well-known/openid-configuration", clientId, clientSecret, redirectUri, nil)
	return &SsoAuthAuthenticator{
		OpenIdConnectAuthenticator: inner,
	}
}
