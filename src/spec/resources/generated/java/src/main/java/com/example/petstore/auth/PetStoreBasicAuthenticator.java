package com.example.petstore.auth;

public final class PetStoreBasicAuthenticator extends BasicAuthenticator {
    public PetStoreBasicAuthenticator(String host, String username, String password) {
        super(host, username, password);
    }
}
