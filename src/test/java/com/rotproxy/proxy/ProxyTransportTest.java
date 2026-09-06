package com.rotproxy.proxy;

import com.rotproxy.config.ProxyProfile;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real loopback TCP/TLS handshakes. No external service or real proxy credentials. */
class ProxyTransportTest {
    private MultiThreadIoEventLoopGroup group;
    @TempDir Path temporary;

    @BeforeEach void start() { group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory()); }
    @AfterEach void stop() { group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly(); }

    @Test void socksWithoutAuthentication() throws Exception { checkRoute(ProxyProfile.ProxyType.SOCKS5, false); }
    @Test void socksWithAuthentication() throws Exception { checkRoute(ProxyProfile.ProxyType.SOCKS5, true); }
    @Test void httpConnectWithoutAuthentication() throws Exception { checkRoute(ProxyProfile.ProxyType.HTTP, false); }
    @Test void httpConnectWithAuthentication() throws Exception { checkRoute(ProxyProfile.ProxyType.HTTP, true); }

    private void checkRoute(ProxyProfile.ProxyType type, boolean auth) throws Exception {
        try (Fixture fixture = new Fixture(type, auth, null, "203.0.113.42", false)) {
            var result = ProxyTransport.probe(fixture.profile(), group, "exit-check.invalid", 80, null);
            assertEquals("203.0.113.42", result.ipAddress());
            assertTrue(result.latencyMs() >= 0);
            fixture.assertFinished();
        }
    }

    @Test void rejectsBadAuthenticationWithoutDirectFallback() throws Exception {
        try (Fixture fixture = new Fixture(ProxyProfile.ProxyType.HTTP, true, null, "203.0.113.42", true)) {
            assertThrows(Exception.class, () -> ProxyTransport.probe(fixture.profile(), group, "exit-check.invalid", 80, null));
            fixture.assertFinished();
        }
    }

    @Test void rejectsInvalidIpResponse() throws Exception {
        try (Fixture fixture = new Fixture(ProxyProfile.ProxyType.SOCKS5, false, null, "not an IP address", false)) {
            assertThrows(Exception.class, () -> ProxyTransport.probe(fixture.profile(), group, "exit-check.invalid", 80, null));
            fixture.assertFinished();
        }
    }

    @Test void httpsConnectUsesTlsAndChecksCertificate() throws Exception {
        // Generate an ephemeral certificate; no private key is stored in the repository.
        Path keyStoreFile = temporary.resolve("loopback.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool").toString();
        String testOnlyPassphrase = "ephemeral-test-store";
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "loopback", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "1", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-storetype", "PKCS12",
                "-keystore", keyStoreFile.toString(), "-storepass", testOnlyPassphrase, "-noprompt").redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        assertEquals(0, process.waitFor());
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream stream = Files.newInputStream(keyStoreFile)) { keyStore.load(stream, testOnlyPassphrase.toCharArray()); }
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keyStore, testOnlyPassphrase.toCharArray());
        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(managers.getKeyManagers(), null, null);
        SslContext trusted = SslContextBuilder.forClient().trustManager((X509Certificate)keyStore.getCertificate("loopback")).build();
        try (Fixture fixture = new Fixture(ProxyProfile.ProxyType.HTTPS, true, serverContext, "203.0.113.42", false)) {
            assertEquals("203.0.113.42", ProxyTransport.probe(fixture.profile(), group, "exit-check.invalid", 80, trusted).ipAddress());
            fixture.assertFinished();
        }
        try (Fixture fixture = new Fixture(ProxyProfile.ProxyType.HTTPS, true, serverContext, "203.0.113.42", false)) {
            assertThrows(Exception.class, () -> ProxyTransport.probe(fixture.profile(), group, "exit-check.invalid", 80, null));
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final ServerSocket listener;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Future<?> server;
        private final ProxyProfile.ProxyType type;
        private final boolean auth;
        private volatile Socket accepted;

        Fixture(ProxyProfile.ProxyType type, boolean auth, SSLContext tls, String response, boolean reject) throws Exception {
            this.type = type;
            this.auth = auth;
            listener = tls == null ? new ServerSocket() : tls.getServerSocketFactory().createServerSocket();
            listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            server = executor.submit(() -> {
                try (Socket socket = listener.accept()) {
                    accepted = socket;
                    socket.setSoTimeout(10000);
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    OutputStream output = socket.getOutputStream();
                    if (type == ProxyProfile.ProxyType.SOCKS5) {
                        assertEquals(5, input.readUnsignedByte());
                        input.readNBytes(input.readUnsignedByte());
                        output.write(new byte[]{5, (byte)(auth ? 2 : 0)}); output.flush();
                        if (auth) {
                            assertEquals(1, input.readUnsignedByte());
                            assertEquals("fixture-user", new String(input.readNBytes(input.readUnsignedByte()), StandardCharsets.UTF_8));
                            assertEquals("fixture-password", new String(input.readNBytes(input.readUnsignedByte()), StandardCharsets.UTF_8));
                            output.write(new byte[]{1, 0}); output.flush();
                        }
                        assertEquals(5, input.readUnsignedByte());
                        assertEquals(1, input.readUnsignedByte());
                        assertEquals(0, input.readUnsignedByte());
                        assertEquals(3, input.readUnsignedByte(), "Destination should stay unresolved until the proxy");
                        assertEquals("exit-check.invalid", new String(input.readNBytes(input.readUnsignedByte()), StandardCharsets.UTF_8));
                        assertEquals(80, input.readUnsignedShort());
                        output.write(new byte[]{5, 0, 0, 1, 127, 0, 0, 1, 0, 80}); output.flush();
                    } else {
                        String connect = headers(input);
                        assertTrue(connect.startsWith("CONNECT exit-check.invalid:80 HTTP/1.1"), connect);
                        if (auth) {
                            String credentials = Base64.getEncoder().encodeToString("fixture-user:fixture-password".getBytes(StandardCharsets.UTF_8));
                            assertTrue(connect.toLowerCase().contains("proxy-authorization: basic " + credentials.toLowerCase()));
                        }
                        output.write((reject ? "HTTP/1.1 407 Proxy Authentication Required\r\nContent-Length: 0\r\n\r\n" : "HTTP/1.1 200 Connection Established\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                        output.flush();
                        if (reject) return;
                    }
                    assertTrue(headers(input).startsWith("GET / HTTP/1.1"));
                    output.write(("HTTP/1.1 200 OK\r\nContent-Length: " + response.length() + "\r\nConnection: close\r\n\r\n" + response).getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                } catch (Exception error) { throw new CompletionException(error); }
            });
        }

        ProxyProfile profile() {
            ProxyProfile profile = new ProxyProfile("Loopback fixture");
            profile.host = InetAddress.getLoopbackAddress().getHostAddress();
            profile.port = listener.getLocalPort();
            profile.type = type;
            if (auth) { profile.username = "fixture-user"; profile.password = "fixture-password"; }
            return profile;
        }

        void assertFinished() throws Exception { server.get(10, TimeUnit.SECONDS); }

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

        @Override public void close() throws Exception {
            listener.close();
            if (accepted != null) accepted.close();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
