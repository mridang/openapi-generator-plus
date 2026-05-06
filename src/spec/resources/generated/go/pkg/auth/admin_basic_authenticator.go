package auth

type AdminBasicAuthenticator struct {
	*BasicAuthenticator
}

func NewAdminBasicAuthenticator(host string, username string, password string) *AdminBasicAuthenticator {
	inner := NewBasicAuthenticator(host, username, password)
	return &AdminBasicAuthenticator{
		BasicAuthenticator: inner,
	}
}
