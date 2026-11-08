package com.example.albumsnap.testutil;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * Guarda o conteudo de um ficheiro de preferencias e repoe-o no fim do teste, para os testes de
 * ecras que mexem no login ou nas definicoes nao estragarem o estado real da app no telemovel.
 */
public final class PrefsBackup {

    private final SharedPreferences prefs;
    private final Map<String, ?> saved;

    private PrefsBackup(SharedPreferences prefs) {
        this.prefs = prefs;
        this.saved = new HashMap<>(prefs.getAll());
    }

    public static PrefsBackup of(Context context, String prefsName) {
        return new PrefsBackup(context.getSharedPreferences(prefsName, Context.MODE_PRIVATE));
    }

    /** Apaga tudo o que esta guardado (o teste comeca "de fresco"). */
    public void clear() {
        prefs.edit().clear().commit();
    }

    /** Repoe exatamente o que la estava antes. */
    public void restore() {
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Map.Entry<String, ?> e : saved.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String) editor.putString(e.getKey(), (String) v);
            else if (v instanceof Long) editor.putLong(e.getKey(), (Long) v);
            else if (v instanceof Integer) editor.putInt(e.getKey(), (Integer) v);
            else if (v instanceof Boolean) editor.putBoolean(e.getKey(), (Boolean) v);
            else if (v instanceof Float) editor.putFloat(e.getKey(), (Float) v);
        }
        editor.commit();
    }
}
