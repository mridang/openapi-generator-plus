package auth

type ServiceTokenAuthenticator struct {
	*BearerAuthenticator
}

func NewServiceTokenAuthenticator(host string, token string) *ServiceTokenAuthenticator {
	inner := NewBearerAuthenticator(host, token)
	return &ServiceTokenAuthenticator{
		BearerAuthenticator: inner,
	}
}
