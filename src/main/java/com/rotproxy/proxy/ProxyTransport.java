package com.rotproxy.proxy;

import com.rotproxy.config.ProxyProfile;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyConnectionEvent;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.NetUtil;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLParameters;

/** Shared tunnel implementation for gameplay and probes; no direct fallback. */
public final class ProxyTransport {
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private ProxyTransport() {}

    public static void install(ChannelPipeline pipeline, ProxyProfile profile) throws Exception {
        install(pipeline, profile, null);
    }

    // An explicit trust context is used only by isolated loopback integration tests.
    static void install(ChannelPipeline pipeline, ProxyProfile profile, SslContext trustContext) throws Exception {
        if (!profile.isValid()) throw new IllegalArgumentException("Invalid proxy host or port");
        InetSocketAddress proxyAddress = new InetSocketAddress(profile.host.trim(), profile.port);
        boolean auth = profile.username != null && !profile.username.isEmpty();
        String password = Objects.requireNonNullElse(profile.password, "");
        ProxyHandler handler = switch (profile.type) {
            case SOCKS5 -> auth ? new Socks5ProxyHandler(proxyAddress, profile.username, password) : new Socks5ProxyHandler(proxyAddress);
            case HTTP, HTTPS -> auth ? new HttpProxyHandler(proxyAddress, profile.username, password) : new HttpProxyHandler(proxyAddress);
        };
        handler.setConnectTimeoutMillis(CONNECT_TIMEOUT_MS);
        pipeline.addFirst("rotproxy_proxy", handler);
        if (profile.type == ProxyProfile.ProxyType.HTTPS) {
            SslContext sslContext = trustContext != null ? trustContext : SslContextBuilder.forClient().build();
            var ssl = sslContext.newHandler(pipeline.channel().alloc(), profile.host.trim(), profile.port);
            SSLParameters parameters = ssl.engine().getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            ssl.engine().setSSLParameters(parameters);
            ssl.setHandshakeTimeoutMillis(CONNECT_TIMEOUT_MS);
            // TLS must wrap both the CONNECT handshake and subsequent tunnel traffic.
            pipeline.addFirst("rotproxy_tls", ssl);
        }
    }

    public static ProbeResult probe(ProxyProfile profile, EventLoopGroup eventLoop) throws Exception {
        return probe(profile, eventLoop, "api.ipify.org", 80, null);
    }

    static ProbeResult probe(ProxyProfile profile, EventLoopGroup eventLoop, String host, int port, SslContext trust) throws Exception {
        long started = System.nanoTime();
        CompletableFuture<ProbeResult> result = new CompletableFuture<>();
        Bootstrap bootstrap = new Bootstrap().group(eventLoop).channel(NioSocketChannel.class)
                .resolver(io.netty.resolver.NoopAddressResolverGroup.INSTANCE)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) throws Exception {
                        install(channel.pipeline(), profile, trust);
                        channel.pipeline().addLast(new HttpClientCodec(), new HttpObjectAggregator(1024),
                                new SimpleChannelInboundHandler<FullHttpResponse>() {
                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
                                if (event instanceof ProxyConnectionEvent) {
                                    var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
                                    request.headers().set(HttpHeaderNames.HOST, host);
                                    request.headers().set(HttpHeaderNames.USER_AGENT, "RotProxy/1.2.1");
                                    request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                                    ctx.writeAndFlush(request);
                                } else ctx.fireUserEventTriggered(event);
                            }

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) {
                                String ip = response.content().toString(StandardCharsets.UTF_8).trim();
                                if (!response.status().equals(HttpResponseStatus.OK)) {
                                    result.completeExceptionally(new IllegalStateException("IP check returned HTTP " + response.status().code()));
                                } else if (!NetUtil.isValidIpV4Address(ip) && !NetUtil.isValidIpV6Address(ip)) {
                                    result.completeExceptionally(new IllegalStateException("IP check did not return a valid IP address"));
                                } else {
                                    result.complete(new ProbeResult(ip, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)));
                                }
                                ctx.close();
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                result.completeExceptionally(cause);
                                ctx.close();
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) {
                                result.completeExceptionally(new IllegalStateException("Proxy closed before the IP check completed"));
                            }
                        });
                    }
                });
        ChannelFuture connection = bootstrap.connect(InetSocketAddress.createUnresolved(host, port));
        connection.addListener(future -> {
            if (!future.isSuccess()) result.completeExceptionally(future.cause());
        });
        try {
            return result.get(7, TimeUnit.SECONDS);
        } finally {
            // Also close a channel that connected successfully but stalled during the probe.
            connection.channel().close().syncUninterruptibly();
        }
    }

    public record ProbeResult(String ipAddress, long latencyMs) {}
}
