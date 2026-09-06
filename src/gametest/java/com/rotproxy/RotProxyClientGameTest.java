package com.rotproxy;

import com.rotproxy.config.ProxyConfig;
import com.rotproxy.gui.RotProxyConfigScreen;
import com.rotproxy.gui.widget.RotProxyButtonWidget;
import com.rotproxy.proxy.ProxyManager;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.EventLoopGroupHolder;

public class RotProxyClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ProxyManager.clearProxy();
            ProxyConfig.resetAllProfiles();
            client.options.guiScale().set(2);
        });
        context.getInput().resizeWindow(854, 480);
        context.setScreen(() -> new JoinMultiplayerScreen(new TitleScreen()));
        context.waitForScreen(JoinMultiplayerScreen.class);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen().children().stream()
                .anyMatch(widget -> widget instanceof RotProxyButtonWidget), "Multiplayer shortcut missing"));
        click(context, "RotProxy: Blocked");
        context.waitForScreen(RotProxyConfigScreen.class);
        context.runOnClient(client -> client.gui.toastManager().clear());
        context.waitTicks(3);
        context.takeScreenshot("rotproxy-26.2-compact");
        context.runOnClient(client -> {
            var screen = client.gui.screen();
            for (var child : screen.children()) {
                if (child instanceof AbstractWidget widget) {
                    check(widget.getX() >= 0 && widget.getRight() <= screen.width, "Widget outside width");
                    check(widget.getY() >= 0 && widget.getBottom() <= screen.height, "Widget outside height");
                }
            }
        });
        click(context, "NEW");
        context.runOnClient(client -> check(ProxyConfig.getProfiles().size() == 2, "NEW did not add profile"));
        click(context, "DELETE");
        context.runOnClient(client -> check(ProxyConfig.getProfiles().size() == 1, "DELETE failed"));
        click(context, "APPLY");
        context.runOnClient(client -> check(ProxyManager.shouldBlockServerConnections(), "Invalid profile bypassed kill switch"));
        context.runOnClient(client -> {
            for (var child : client.gui.screen().children()) {
                if (child instanceof EditBox field && field.getMessage().getString().equals("Profile name")) field.setValue("UI resize check");
            }
        });
        context.getInput().resizeWindow(1280, 800);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen().children().stream().anyMatch(child ->
                child instanceof EditBox field && field.getValue().equals("UI resize check")), "Resize lost edited fields"));
        context.takeScreenshot("rotproxy-26.2-desktop");
        click(context, "CLOSE");
        context.waitForScreen(JoinMultiplayerScreen.class);
        context.runOnClient(client -> {
            var data = new ServerData("Loopback test", "127.0.0.1:1", ServerData.Type.OTHER);
            try { new ServerStatusPinger().pingServer(data, () -> {}, () -> {}, EventLoopGroupHolder.remote(false)); }
            catch (java.net.UnknownHostException unexpected) { throw new AssertionError(unexpected); }
            check(data.state() == ServerData.State.UNREACHABLE, "Disabled server ping was not blocked");
            ConnectScreen.startConnecting(client.gui.screen(), client, ServerAddress.parseString(data.ip), data, false, null);
            check(client.gui.screen() instanceof DisconnectedScreen, "Direct connect was not blocked");
            EmbeddedChannel local = new EmbeddedChannel();
            Connection.configureSerialization(local.pipeline(), PacketFlow.CLIENTBOUND, true, null);
            check(local.pipeline().get("rotproxy_proxy") == null, "Singleplayer incorrectly proxied");
            local.finishAndReleaseAll();
            EmbeddedChannel remote = new EmbeddedChannel();
            boolean blocked = false;
            try { Connection.configureSerialization(remote.pipeline(), PacketFlow.CLIENTBOUND, false, null); }
            catch (IllegalStateException expected) { blocked = true; }
            finally { remote.finishAndReleaseAll(); }
            check(blocked, "Low-level connection bypassed kill switch");
            ProxyConfig.resetAllProfiles();
            ProxyConfig.save();
        });
        verifyLiveTunnelAndHeartbeat(context);
        context.setScreen(TitleScreen::new);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void verifyLiveTunnelAndHeartbeat(ClientGameTestContext context) {
        try (LoopbackProxy proxy = new LoopbackProxy()) {
            context.runOnClient(client -> {
                var profile = proxy.profile();
                ProxyConfig.getProfiles().set(0, profile);
                ProxyManager.applyProxy(profile);
            });
            context.waitFor(client -> ProxyManager.isProxyHealthy(), 400);
            // This destination has no server. Success proves the proxy intercepted connect().
            Connection connection = new Connection(PacketFlow.CLIENTBOUND);
            var future = Connection.connect(new java.net.InetSocketAddress("192.0.2.1", 25565), EventLoopGroupHolder.remote(false), connection);
            context.waitFor(client -> future.isDone() && proxy.gameTunnels.get() > 0, 400);
            check(future.isSuccess(), "Minecraft proxy tunnel did not connect");
            check(future.channel().pipeline().get("rotproxy_proxy") != null, "Missing runtime proxy handler");
            context.setScreen(() -> new RotProxyConfigScreen(new TitleScreen()));
            context.runOnClient(client -> client.gui.toastManager().clear());
            context.waitTicks(3);
            context.takeScreenshot("rotproxy-26.2-protected");
            proxy.failProbes = true;
            context.waitFor(client -> ProxyManager.getStatus() == ProxyManager.Status.ERROR, 600);
            context.waitFor(client -> !future.channel().isOpen(), 100);
            check(ProxyManager.shouldBlockServerConnections(), "Heartbeat failure did not engage kill switch");
            proxy.failProbes = false;
            context.waitFor(client -> ProxyManager.isProxyHealthy(), 600);
            context.runOnClient(client -> {
                ProxyManager.clearProxy();
                ProxyConfig.resetAllProfiles();
                ProxyConfig.save();
            });
        } catch (java.io.IOException error) { throw new AssertionError(error); }
    }

    private static void click(ClientGameTestContext context, String label) {
        context.runOnClient(client -> {
            var screen = client.gui.screen();
            var target = screen.children().stream()
                    .filter(child -> child instanceof RotProxyButtonWidget button && button.getMessage().getString().equals(label))
                    .map(child -> (RotProxyButtonWidget) child).findFirst().orElseThrow(() -> new AssertionError("Missing button: " + label));
            var event = new net.minecraft.client.input.MouseButtonEvent(target.getX() + target.getWidth() / 2.0,
                    target.getY() + target.getHeight() / 2.0, new net.minecraft.client.input.MouseButtonInfo(0, 0));
            check(screen.mouseClicked(event, false), "Button did not accept click: " + label);
            screen.mouseReleased(event);
        });
        context.waitTick();
    }
}
