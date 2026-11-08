package com.example.albumsnap.util;

import android.os.Handler;
import android.os.Looper;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Corre trabalho (rede, base de dados) fora da UI thread e devolve o resultado
 * na UI thread (ExecutorService + Handler, como nas aulas de threads).
 */
public final class Background {

    /** Trabalho que pode falhar. */
    public interface Task<T> {
        T call() throws Exception;
    }

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // Onde correm os callbacks: na UI thread. Os testes JVM (que nao tem UI thread) trocam-no.
    private static volatile Executor callbackExecutor = command -> MAIN.post(command);

    private Background() {
    }

    public static <T> void run(final Task<T> task, final Callback<T> callback) {
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final T result = task.call();
                    callbackExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(result);
                        }
                    });
                } catch (final Exception e) {
                    final String message = describe(e);
                    callbackExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(message);
                        }
                    });
                }
            }
        });
    }

    /** So para testes JVM: os callbacks correm logo na thread de fundo (nao ha UI thread). */
    public static void runCallbacksImmediatelyForTests() {
        callbackExecutor = Runnable::run;
    }

    /** Repoe o normal: callbacks na UI thread. */
    public static void restoreMainThreadCallbacks() {
        callbackExecutor = command -> MAIN.post(command);
    }

    static String describe(Exception e) {
        if (e instanceof UnknownHostException
                || e instanceof ConnectException
                || e instanceof SocketTimeoutException) {
            return ErrorMessages.NETWORK;
        }
        String message = e.getMessage();
        return message != null && !message.isEmpty() ? message : e.getClass().getSimpleName();
    }
}
