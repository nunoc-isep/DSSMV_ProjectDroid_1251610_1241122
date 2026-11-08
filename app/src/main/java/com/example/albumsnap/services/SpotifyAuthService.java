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

    /**
     * Pede um access token novo com o refresh token, mesmo que o atual ainda parecesse valido
     * (usado quando o Spotify responde 401). Se o Spotify recusar o refresh token, a sessao e
     * apagada e lanca IOException(ErrorMessages.SESSION_EXPIRED).
     * BLOQUEANTE: so pode ser chamado numa thread de fundo.
     */
    String refreshAccessToken() throws IOException;

    boolean isAuthenticated();

    void disconnect();
}