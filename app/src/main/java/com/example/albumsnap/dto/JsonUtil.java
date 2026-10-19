package com.example.albumsnap.dto;

import org.json.JSONObject;

/** Leitura segura de campos JSON (evita receber o texto "null"). */
final class JsonUtil {

    private JsonUtil() {
    }

    static String str(JSONObject o, String key) {
        return o == null || o.isNull(key) ? "" : o.optString(key, "");
    }
}
