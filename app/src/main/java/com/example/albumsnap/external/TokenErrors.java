package com.example.albumsnap.external;

import org.json.JSONException;
import org.json.JSONObject;

/** Interpreta os erros do servidor de tokens do Spotify (Java puro, para ser facil de testar). */
final class TokenErrors {

    private TokenErrors() {
    }

    /**
     * True se o Spotify recusou o refresh token de vez: HTTP 400 com {"error": "invalid_grant"}.
     * Acontece quando o utilizador revoga o acesso da app, ou o token expirou ou foi invalidado.
     * Nao conta: falhas de rede, erros 5xx ou outros 4xx (nesses a sessao pode ainda estar boa).
     */
    static boolean isRevokedGrant(int status, String body) {
        if (status != 400 || body == null || body.isEmpty()) return false;
        try {
            return "invalid_grant".equals(new JSONObject(body).optString("error", ""));
        } catch (JSONException e) {
            return false;   // corpo que nao e JSON
        }
    }
}
