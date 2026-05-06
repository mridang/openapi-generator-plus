package auth

type PetStoreBasicAuthenticator struct {
	*BasicAuthenticator
}

func NewPetStoreBasicAuthenticator(host string, username string, password string) *PetStoreBasicAuthenticator {
	inner := NewBasicAuthenticator(host, username, password)
	return &PetStoreBasicAuthenticator{
		BasicAuthenticator: inner,
	}
}
