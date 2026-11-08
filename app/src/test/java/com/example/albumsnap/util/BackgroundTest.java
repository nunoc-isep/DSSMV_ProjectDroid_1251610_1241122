package com.example.albumsnap.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

import com.example.albumsnap.testutil.Await;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/** Trabalho fora da UI thread: resultado, erros e traducao dos erros de rede. */
public class BackgroundTest {

    @Before
    public void setUp() {
        Background.runCallbacksImmediatelyForTests();   // nos testes JVM nao ha UI thread
    }

    @After
    public void tearDown() {
        Background.restoreMainThreadCallbacks();
    }

    // --------------------------------------------------------------- describe

    @Test
    public void describe_networkFailures_giveTheNetworkCode() {
        assertEquals(ErrorMessages.NETWORK, Background.describe(new UnknownHostException("api.spotify.com")));
        assertEquals(ErrorMessages.NETWORK, Background.describe(new ConnectException("recusada")));
        assertEquals(ErrorMessages.NETWORK, Background.describe(new SocketTimeoutException("tempo")));
    }

    @Test
    public void describe_otherErrors_useTheirOwnMessage() {
        assertEquals("sessao expirada", Background.describe(new IOException("sessao expirada")));
        assertEquals(ErrorMessages.NO_SESSION, Background.describe(new IOException(ErrorMessages.NO_SESSION)));
    }

    @Test
    public void describe_withoutAMessage_usesTheClassName() {
        assertEquals("IOException", Background.describe(new IOException()));
        assertEquals("IllegalStateException", Background.describe(new IllegalStateException("")));
    }

    // -------------------------------------------------------------------- run

    @Test
    public void run_deliversTheResultToTheCallback() {
        Await<String> callback = new Await<>();

        Background.run(() -> "pronto", callback);

        assertEquals("pronto", callback.result());
    }

    @Test
    public void run_deliversNullResultsToo() {
        Await<String> callback = new Await<>();

        Background.run(() -> null, callback);

        assertEquals(null, callback.result());
    }

    @Test
    public void run_aFailingTaskGoesToOnErrorWithTheDescribedMessage() {
        Await<String> callback = new Await<>();

        Background.run(() -> {
            throw new IOException("falhou");
        }, callback);

        assertEquals("falhou", callback.error());
    }

    @Test
    public void run_aNetworkFailureIsReportedAsTheNetworkCode() {
        Await<String> callback = new Await<>();

        Background.run(() -> {
            throw new UnknownHostException("sem internet");
        }, callback);

        assertEquals(ErrorMessages.NETWORK, callback.error());
    }

    @Test
    public void run_unexpectedRuntimeExceptionsAreReportedToo() {
        Await<String> callback = new Await<>();

        Background.run(() -> {
            throw new IllegalArgumentException("argumento mau");
        }, callback);

        assertEquals("argumento mau", callback.error());
    }

    @Test
    public void run_theTaskDoesNotRunOnTheCallingThread() {
        Await<Thread> callback = new Await<>();

        Background.run(Thread::currentThread, callback);

        assertNotEquals(Thread.currentThread(), callback.result());
    }

    @Test
    public void run_severalTasksAtOnceAllFinish() {
        Await<Integer> a = new Await<>();
        Await<Integer> b = new Await<>();
        Await<Integer> c = new Await<>();

        Background.run(() -> 1, a);
        Background.run(() -> 2, b);
        Background.run(() -> 3, c);

        assertEquals(6, a.result() + b.result() + c.result());
        assertSame(a.result(), a.result());
    }
}
