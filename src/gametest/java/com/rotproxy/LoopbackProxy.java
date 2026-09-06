package com.rotproxy;

import com.rotproxy.config.ProxyProfile;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** An isolated HTTP CONNECT endpoint; it never opens an external connection. */
final class LoopbackProxy implements AutoCloseable {
    private final ServerSocket server = new ServerSocket();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final java.util.Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    final AtomicInteger gameTunnels = new AtomicInteger();
    volatile boolean failProbes;

    LoopbackProxy() throws IOException {
        server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        executor.submit(() -> {
            while (!server.isClosed()) {
                try {
                    Socket socket = server.accept();
                    sockets.add(socket);
                    executor.submit(() -> serve(socket));
                } catch (IOException closed) { break; }
            }
        });
    }

    private void serve(Socket socket) {
        try (socket) {
            socket.setSoTimeout(30000);
            String connect = headers(socket.getInputStream());
            OutputStream output = socket.getOutputStream();
            output.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
            if (connect.startsWith("CONNECT api.ipify.org:80 ")) {
                headers(socket.getInputStream());
                String reply = failProbes ? "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n"
                        : "HTTP/1.1 200 OK\r\nContent-Length: 12\r\n\r\n203.0.113.42";
                output.write(reply.getBytes(StandardCharsets.US_ASCII));
                output.flush();
            } else {
                gameTunnels.incrementAndGet();
                while (socket.getInputStream().read() != -1) { /* hold until the client closes */ }
            }
        } catch (IOException ignored) {
            // Socket teardown is expected when the kill switch closes the tunnel.
        } finally { sockets.remove(socket); }
    }

    ProxyProfile profile() {
        ProxyProfile profile = new ProxyProfile("Loopback test proxy");
        profile.host = InetAddress.getLoopbackAddress().getHostAddress();
        profile.port = server.getLocalPort();
        profile.type = ProxyProfile.ProxyType.HTTP;
        return profile;
    }

    private static String headers(InputStream input) throws IOException {
        StringBuilder text = new StringBuilder();
        while (text.length() < 8192) {
            int next = input.read();
            if (next < 0) throw new EOFException();
            text.append((char)next);
            if (text.toString().endsWith("\r\n\r\n")) return text.toString();
        }
        throw new IOException("Headers too large");
    }

    @Override public void close() throws IOException {
        server.close();
        for (Socket socket : sockets) socket.close();
        executor.shutdownNow();
    }
}
