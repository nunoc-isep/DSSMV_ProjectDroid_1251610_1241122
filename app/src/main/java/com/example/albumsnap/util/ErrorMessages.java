package com.example.albumsnap.util;

import android.content.Context;

import com.example.albumsnap.R;

/**
 * Mensagens de erro traduziveis.
 *
 * As camadas de baixo (Background, SpotifyAuthManager...) nao tem o Context da Activity e o
 * Context da aplicacao pode nao refletir o idioma escolhido nas Definicoes (Android < 13).
 * Por isso devolvem um CODIGO (as constantes abaixo) e a Activity traduz-o com localize().
 * Qualquer outra mensagem (ex.: erro tecnico de uma excecao) passa tal e qual.
 */
public final class ErrorMessages {

    public static final String NETWORK = "error.network";
    public static final String NO_SESSION = "error.no_session";
    public static final String LOGIN_CANCELLED = "error.login_cancelled";
    public static final String LOGIN_INVALID = "error.login_invalid";
    public static final String LOGIN_TOKEN_FAILED = "error.login_token_failed";
    public static final String UNEXPECTED_RESPONSE = "error.unexpected_response";
    public static final String SESSION_EXPIRED = "error.session_expired";
    public static final String SPOTIFY_FORBIDDEN = "error.spotify_forbidden";
    public static final String SPOTIFY_NOT_FOUND = "error.spotify_not_found";
    public static final String SPOTIFY_RATE_LIMIT = "error.spotify_rate_limit";
    /** Erro HTTP generico do Spotify: o codigo e este prefixo seguido do estado (ex.: "error.spotify_http:500"). */
    public static final String SPOTIFY_HTTP_PREFIX = "error.spotify_http:";

    private ErrorMessages() {
    }

    public static String spotifyHttp(int status) {
        return SPOTIFY_HTTP_PREFIX + status;
    }

    /** Texto no idioma da Activity para um codigo; as restantes mensagens ficam como estao. */
    public static String localize(Context context, String message) {
        if (message == null) return "";
        switch (message) {
            case NETWORK:
                return context.getString(R.string.error_network);
            case NO_SESSION:
                return context.getString(R.string.error_no_session);
            case LOGIN_CANCELLED:
                return context.getString(R.string.login_error_cancelled);
            case LOGIN_INVALID:
                return context.getString(R.string.login_error_invalid);
            case LOGIN_TOKEN_FAILED:
                return context.getString(R.string.login_error_token);
            case UNEXPECTED_RESPONSE:
                return context.getString(R.string.error_unexpected_response);
            case SESSION_EXPIRED:
                return context.getString(R.string.error_session_expired);
            case SPOTIFY_FORBIDDEN:
                return context.getString(R.string.error_spotify_forbidden);
            case SPOTIFY_NOT_FOUND:
                return context.getString(R.string.error_spotify_not_found);
            case SPOTIFY_RATE_LIMIT:
                return context.getString(R.string.error_spotify_rate_limit);
            default:
                if (message.startsWith(SPOTIFY_HTTP_PREFIX)) {
                    try {
                        int status = Integer.parseInt(message.substring(SPOTIFY_HTTP_PREFIX.length()));
                        return context.getString(R.string.error_spotify_http, status);
                    } catch (NumberFormatException ignored) {
                        // nao e um codigo valido: mostra a mensagem como veio
                    }
                }
                return message;
        }
    }
}
