package com.example.albumsnap.views;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.example.albumsnap.util.ErrorMessages;

/**
 * Quando o Spotify revoga ou termina a sessao, o utilizador nao pode ficar num ecra a receber
 * erros: avisa-se e volta-se ao login (a sessao antiga ja foi apagada pelo SpotifyAuthManager).
 */
final class SessionExpiry {

    private SessionExpiry() {
    }

    /** True se a mensagem de erro quer dizer que ja nao ha sessao valida no Spotify. */
    static boolean isSessionLost(String message) {
        return ErrorMessages.SESSION_EXPIRED.equals(message) || ErrorMessages.NO_SESSION.equals(message);
    }

    /**
     * Se a sessao acabou: mostra o aviso e abre o login, fechando os ecras anteriores (devolve true).
     * Senao nao faz nada (devolve false) e o chamador trata o erro como habitualmente.
     */
    static boolean handle(Activity activity, String message) {
        if (!isSessionLost(message)) return false;
        Toast.makeText(activity, ErrorMessages.localize(activity, message), Toast.LENGTH_LONG).show();
        Intent intent = new Intent(activity, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
        return true;
    }
}
