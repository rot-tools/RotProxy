package com.rotproxy.proxy;

import com.rotproxy.RotProxyMod;
import com.rotproxy.config.ProxyConfig;
import com.rotproxy.config.ProxyProfile;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.net.SocketAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

public final class ProxyManager {
    public static final long HEARTBEAT_INTERVAL_SECONDS = 15;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final ChannelGroup GAME_CHANNELS = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private static volatile Status status = Status.DISABLED;
    private static volatile String currentIp = "Unknown", lastError = "";
    private static volatile long latencyMs = -1, lastProbeAt = -1;
    // Each activation has a unique snapshot identity. Old probe results cannot re-enable it.
    private static volatile ProxyProfile activeProfile;
    private static ScheduledExecutorService scheduler;
    private static ExecutorService worker;
    private static MultiThreadIoEventLoopGroup eventLoop;
    private static ScheduledFuture<?> heartbeat;

    private ProxyManager() {}

    public static synchronized void initialize() {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory("RotProxy-Heartbeat"));
        worker = Executors.newSingleThreadExecutor(daemonFactory("RotProxy-Worker"));
        eventLoop = new MultiThreadIoEventLoopGroup(1, daemonFactory("RotProxy-Probe"), NioIoHandler.newFactory());
    }

    private static ThreadFactory daemonFactory(String name) {
        return runnable -> { Thread thread = new Thread(runnable, name); thread.setDaemon(true); return thread; };
    }

    public static void restoreConfiguredProxy() {
        if (ProxyConfig.isEnabled()) applyProxy(ProxyConfig.getActiveProfile());
    }

    public static synchronized void applyProxy(ProxyProfile profile) {
        initialize();
        if (profile == null || !profile.isValid()) { clearProxy(); return; }
        stopHeartbeat();
        status = Status.CONNECTING;
        GAME_CHANNELS.close();
        activeProfile = profile.copy();
        currentIp = "Checking...";
        latencyMs = -1;
        lastProbeAt = -1;
        lastError = "";
        ProxyConfig.setEnabled(true);
        ProxyConfig.save();
        ProxyProfile snapshot = activeProfile;
        refresh(snapshot);
        heartbeat = scheduler.scheduleAtFixedRate(() -> refresh(snapshot), HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public static synchronized void clearProxy() {
        stopHeartbeat();
        status = Status.DISABLED;
        activeProfile = null;
        GAME_CHANNELS.close();
        currentIp = "Unknown";
        lastError = "";
        latencyMs = lastProbeAt = -1;
        ProxyConfig.setEnabled(false);
        ProxyConfig.save();
    }

    private static void stopHeartbeat() {
        if (heartbeat != null) { heartbeat.cancel(false); heartbeat = null; }
    }

    public static synchronized void shutdown() {
        stopHeartbeat();
        activeProfile = null;
        status = Status.DISABLED;
        GAME_CHANNELS.close();
        if (scheduler != null) scheduler.shutdownNow();
        if (worker != null) worker.shutdownNow();
        if (eventLoop != null) eventLoop.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        scheduler = null;
    }

    public static synchronized void installGameProxy(ChannelPipeline pipeline) {
        ProxyProfile snapshot = activeProfile;
        if (status != Status.CONNECTED || snapshot == null) {
            throw new IllegalStateException("RotProxy blocked this connection: no healthy proxy");
        }
        try {
            ProxyTransport.install(pipeline, snapshot);
        } catch (Exception failure) {
            pipeline.channel().close();
            throw new IllegalStateException("RotProxy could not initialize the proxy tunnel", failure);
        }
        GAME_CHANNELS.add(pipeline.channel());
        pipeline.addLast("rotproxy_guard", new ChannelOutboundHandlerAdapter() {
            @Override
            public void connect(ChannelHandlerContext ctx, SocketAddress remote, SocketAddress local, ChannelPromise promise) {
                synchronized (ProxyManager.class) {
                    if (status != Status.CONNECTED || activeProfile != snapshot) {
                        promise.setFailure(new IllegalStateException("RotProxy blocked a stale connection"));
                        ctx.close();
                        return;
                    }
                    ctx.connect(remote, local, promise);
                }
            }
        });
    }

    private static void refresh(ProxyProfile snapshot) {
        synchronized (ProxyManager.class) {
            if (activeProfile != snapshot) return;
        }
        runProbe(snapshot).whenComplete((result, failure) -> {
            boolean notifyFailure = false;
            synchronized (ProxyManager.class) {
                if (activeProfile != snapshot) return;
                lastProbeAt = System.currentTimeMillis();
                if (failure == null) {
                    currentIp = result.ipAddress();
                    latencyMs = result.latencyMs();
                    lastError = "";
                    status = Status.CONNECTED;
                } else {
                    notifyFailure = status == Status.CONNECTED;
                    status = Status.ERROR;
                    currentIp = "Unknown";
                    latencyMs = -1;
                    lastError = describeException(failure, snapshot);
                    GAME_CHANNELS.close();
                    RotProxyMod.LOGGER.warn("RotProxy proxy check failed: {}", lastError);
                }
            }
            if (notifyFailure) notifyKillSwitch();
        });
    }

    private static CompletableFuture<ProxyTransport.ProbeResult> runProbe(ProxyProfile profile) {
        initialize();
        ProxyProfile snapshot = profile.copy();
        return CompletableFuture.supplyAsync(() -> {
            try { return ProxyTransport.probe(snapshot, eventLoop); }
            catch (Exception failure) { throw new CompletionException(failure); }
        }, worker);
    }

    public static CompletableFuture<ProbeOutcome> testProfile(ProxyProfile profile) {
        if (profile == null || !profile.isValid()) return CompletableFuture.completedFuture(ProbeOutcome.failure("Enter a valid host and port."));
        ProxyProfile snapshot = profile.copy();
        return runProbe(snapshot).handle((result, failure) -> failure == null
                ? new ProbeOutcome(true, result.ipAddress(), result.latencyMs(), "")
                : ProbeOutcome.failure(describeException(failure, snapshot)));
    }

    private static String describeException(Throwable failure, ProxyProfile profile) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause() != null) failure = failure.getCause();
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        // Diagnostics must never echo credentials returned by a proxy error.
        if (profile.password != null && !profile.password.isEmpty()) message = message.replace(profile.password, "[redacted]");
        if (profile.username != null && !profile.username.isEmpty()) message = message.replace(profile.username, "[redacted]");
        return message.replace('\n', ' ').replace('\r', ' ');
    }

    private static void notifyKillSwitch() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        client.execute(() -> SystemToast.addOrUpdate(client.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.literal("RotProxy Kill Switch"), Component.literal("Proxy check failed. Multiplayer connections closed.")));
    }

    public static Component getKillSwitchReasonText() {
        return Component.literal(switch (status) {
            case CONNECTED -> "RotProxy is healthy.";
            case CONNECTING -> "RotProxy is verifying the proxy. Wait for CONNECTED before joining.";
            case DISABLED -> "RotProxy blocks multiplayer while disabled. Apply a healthy proxy first.";
            case ERROR -> "RotProxy blocked multiplayer because the proxy check failed: " + lastError;
        });
    }

    public static boolean shouldBlockServerConnections() { return status != Status.CONNECTED; }
    public static boolean isProxyHealthy() { return status == Status.CONNECTED; }
    public static boolean isEnabled() { return ProxyConfig.isEnabled(); }
    public static Status getStatus() { return status; }
    public static String getCurrentIp() { return currentIp; }
    public static String getLastError() { return lastError; }
    public static long getLatencyMs() { return latencyMs; }
    public static String getActiveProfileName() { var profile = activeProfile; return profile == null ? "None" : profile.name; }
    public static String getLastProbeLabel() { return lastProbeAt < 0 ? "never" : TIME.format(Instant.ofEpochMilli(lastProbeAt)); }
    public static String getHeartbeatSummary() { return "Every 15s | last check " + getLastProbeLabel(); }
    public static String getKillSwitchStateLabel() { return shouldBlockServerConnections() ? "BLOCKING" : "SAFE"; }
    public enum Status { DISABLED, CONNECTING, CONNECTED, ERROR }
    public record ProbeOutcome(boolean success, String ipAddress, long latencyMs, String errorMessage) {
        public static ProbeOutcome failure(String message) { return new ProbeOutcome(false, "Unknown", -1, message); }
    }
}
