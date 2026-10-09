package com.example.albumsnap.services;

import java.io.IOException;

/** Contrato do diagrama de classes (package services). */
public interface SpotifyAuthService {

    /** Abre o login do Spotify no browser. */
    void authorize();

    /**
     * Devolve um access token valido (renova-o se tiver expirado).
     * BLOQUEANTE: so pode ser chamado numa thread de fundo.
     */
    String getAccessToken() throws IOException;

    boolean isAuthenticated();

    void disconnect();
}