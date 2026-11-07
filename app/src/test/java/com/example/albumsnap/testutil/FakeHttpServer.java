package com.example.albumsnap.testutil;

import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servidor HTTP local (so para testes JVM) que faz de Spotify, Deezer ou iTunes: responde o que
 * o teste mandar e guarda os pedidos recebidos para o teste os poder verificar.
 *
 * Usa apenas java.net.ServerSocket (modulo java.base, presente em qualquer JDK), em vez de
 * com.sun.net.httpserver, que nem todos os JDK/IDE expoem. Implementa so o HTTP/1.1 que o
 * HttpURLConnection usa: uma resposta por ligacao ("Connection: close").
 */
public final class FakeHttpServer implements AutoCloseable {

    /** Um pedido recebido pelo servidor. */
    public static final class RecordedRequest {
        public final String method;
        public final String path;       // ja descodificado, sem query
        public final String rawQuery;   // como chegou (pode ser null)
        public final String body;
        private final Map<String, String> headers;

        RecordedRequest(String method, String path, String rawQuery, String body, Map<String, String> headers) {
            this.method = method;
            this.path = path;
            this.rawQuery = rawQuery;
            this.body = body;
            this.headers = headers;
        }

        /** Valor de um cabecalho (nome sem distinguir maiusculas), ou null. */
        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        /** Valor (ja descodificado) de um parametro da query, ou null. */
        public String queryParam(String name) {
            if (rawQuery == null) return null;
            for (String pair : rawQuery.split("&")) {
                int eq = pair.indexOf('=');
                String key = eq < 0 ? pair : pair.substring(0, eq);
                if (decodeQuery(key).equals(name)) return eq < 0 ? "" : decodeQuery(pair.substring(eq + 1));
            }
            return null;
        }

        private static String decodeQuery(String s) {
            try {
                return URLDecoder.decode(s, "UTF-8");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Resposta que o servidor devolve. */
    public static final class Reply {
        final int status;
        final String body;

        public Reply(int status, String body) {
            this.status = status;
            this.body = body == null ? "" : body;
        }
    }

    public interface Handler {
        Reply handle(RecordedRequest request);
    }

    private static final int READ_TIMEOUT_MS = 5000;

    private final ServerSocket serverSocket;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Handler> routes = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public FakeHttpServer() throws IOException {
        // Porta 0 = o sistema escolhe uma livre; so aceita ligacoes deste computador
        serverSocket = new ServerSocket(0, 50, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
        Thread acceptor = new Thread(this::acceptLoop, "FakeHttpServer-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /** Ex.: "http://127.0.0.1:54321" (sem barra no fim). */
    public String baseUrl() {
        return "http://127.0.0.1:" + serverSocket.getLocalPort();
    }

    /** Resposta calculada a partir do pedido (por metodo e caminho exato). */
    public void on(String method, String path, Handler handler) {
        routes.put(method + " " + path, handler);
    }

    /** Resposta fixa. */
    public void reply(String method, String path, int status, String body) {
        on(method, path, request -> new Reply(status, body));
    }

    /** Todos os pedidos recebidos, pela ordem de chegada. */
    public List<RecordedRequest> requests() {
        return new ArrayList<>(requests);
    }

    /** Pedidos recebidos com este metodo e caminho. */
    public List<RecordedRequest> requests(String method, String path) {
        List<RecordedRequest> out = new ArrayList<>();
        for (RecordedRequest r : requests) {
            if (r.method.equals(method) && r.path.equals(path)) out.add(r);
        }
        return out;
    }

    // ------------------------------------------------------------ servidor

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket socket = serverSocket.accept();
                Thread worker = new Thread(() -> serve(socket), "FakeHttpServer-worker");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException e) {
                if (closed) return;   // close() fechou o socket: acabou
            }
        }
    }

    private void serve(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(READ_TIMEOUT_MS);
            RecordedRequest request = readRequest(new BufferedInputStream(s.getInputStream()));
            if (request == null) return;
            requests.add(request);   // registado antes de responder: o teste ve-o assim que a chamada volta

            Handler handler = routes.get(request.method + " " + request.path);
            Reply reply = handler != null ? handler.handle(request) : new Reply(404, "{}");
            writeResponse(s.getOutputStream(), reply);
        } catch (IOException e) {
            // cliente desligou ou excedeu o tempo: nao ha nada a fazer
        }
    }

    private static RecordedRequest readRequest(InputStream in) throws IOException {
        String requestLine = readLine(in);
        if (requestLine == null || requestLine.isEmpty()) return null;
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) return null;
        String method = parts[0];
        String target = parts[1];

        Map<String, String> headers = new ConcurrentHashMap<>();
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.putIfAbsent(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).trim());
            }
        }

        int question = target.indexOf('?');
        String path = percentDecode(question < 0 ? target : target.substring(0, question));
        String rawQuery = question < 0 ? null : target.substring(question + 1);
        return new RecordedRequest(method, path, rawQuery, readBody(in, headers), headers);
    }

    private static String readBody(InputStream in, Map<String, String> headers) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String transferEncoding = headers.get("transfer-encoding");
        if (transferEncoding != null && transferEncoding.toLowerCase(Locale.ROOT).contains("chunked")) {
            while (true) {
                String sizeLine = readLine(in);
                if (sizeLine == null) break;
                int semicolon = sizeLine.indexOf(';');
                int size = Integer.parseInt((semicolon < 0 ? sizeLine : sizeLine.substring(0, semicolon)).trim(), 16);
                if (size == 0) {
                    readLine(in);   // linha em branco final
                    break;
                }
                copyExactly(in, body, size);
                readLine(in);       // CRLF depois de cada bloco
            }
        } else if (headers.containsKey("content-length")) {
            copyExactly(in, body, Integer.parseInt(headers.get("content-length")));
        }
        return new String(body.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void copyExactly(InputStream in, ByteArrayOutputStream out, int count) throws IOException {
        byte[] buffer = new byte[4096];
        int remaining = count;
        while (remaining > 0) {
            int n = in.read(buffer, 0, Math.min(buffer.length, remaining));
            if (n < 0) throw new IOException("ligacao fechada a meio do corpo do pedido");
            out.write(buffer, 0, n);
            remaining -= n;
        }
    }

    /** Le uma linha terminada em \n (com ou sem \r); null se a ligacao fechou antes de qualquer byte. */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        boolean any = false;
        while ((b = in.read()) != -1) {
            any = true;
            if (b == '\n') break;
            line.write(b);
        }
        if (!any) return null;
        String text = new String(line.toByteArray(), StandardCharsets.ISO_8859_1);
        return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
    }

    private static void writeResponse(OutputStream out, Reply reply) throws IOException {
        byte[] body = reply.body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + reply.status + " " + reasonPhrase(reply.status) + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private static String reasonPhrase(int status) {
        switch (status) {
            case 200: return "OK";
            case 201: return "Created";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 429: return "Too Many Requests";
            case 500: return "Internal Server Error";
            default: return "Status " + status;
        }
    }

    /** Descodifica %XX (em UTF-8) sem tratar "+" como espaco, que e o certo num caminho. */
    private static String percentDecode(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                try {
                    out.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                    i += 3;
                    continue;
                } catch (NumberFormatException e) {
                    // nao era uma sequencia %XX: fica como esta
                }
            }
            int codePoint = s.codePointAt(i);
            byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
            out.write(bytes, 0, bytes.length);
            i += Character.charCount(codePoint);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        closed = true;
        try {
            serverSocket.close();
        } catch (IOException e) {
            // ja estava fechado
        }
    }
}
