package com.example.albumsnap.testutil;

import com.example.albumsnap.util.Callback;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Callback para testes: o teste espera (no maximo 5 s) pelo resultado ou pelo erro. */
public final class Await<T> implements Callback<T> {

    private final CountDownLatch done = new CountDownLatch(1);
    private volatile T result;
    private volatile String error;
    private volatile boolean succeeded;

    @Override
    public void onResult(T result) {
        this.result = result;
        this.succeeded = true;
        done.countDown();
    }

    @Override
    public void onError(String message) {
        this.error = message;
        this.succeeded = false;
        done.countDown();
    }

    /** O resultado; falha o teste se veio um erro ou se o callback nao foi chamado. */
    public T result() {
        waitForCallback();
        if (!succeeded) throw new AssertionError("esperava um resultado, mas veio o erro: " + error);
        return result;
    }

    /** A mensagem de erro; falha o teste se veio um resultado ou se o callback nao foi chamado. */
    public String error() {
        waitForCallback();
        if (succeeded) throw new AssertionError("esperava um erro, mas veio o resultado: " + result);
        return error;
    }

    private void waitForCallback() {
        try {
            if (!done.await(5, TimeUnit.SECONDS)) throw new AssertionError("o callback nao foi chamado em 5 s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrompido a esperar pelo callback");
        }
    }
}
