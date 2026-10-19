package com.example.albumsnap.util;

/** Resultado de uma operacao assincrona; chamado sempre na UI thread. */
public interface Callback<T> {
    void onResult(T result);

    void onError(String message);
}
