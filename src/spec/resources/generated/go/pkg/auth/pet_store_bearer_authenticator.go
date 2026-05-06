package auth

type PetStoreBearerAuthenticator struct {
	*BearerAuthenticator
}

func NewPetStoreBearerAuthenticator(host string, token string) *PetStoreBearerAuthenticator {
	inner := NewBearerAuthenticator(host, token)
	return &PetStoreBearerAuthenticator{
		BearerAuthenticator: inner,
	}
}
