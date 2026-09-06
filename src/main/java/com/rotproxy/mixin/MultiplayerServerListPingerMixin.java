package com.rotproxy.mixin;

import com.rotproxy.proxy.ProxyManager;
import java.net.InetSocketAddress;
import java.util.List;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerStatusPinger.class)
public abstract class MultiplayerServerListPingerMixin {
    @Inject(method = "pingServer", at = @At("HEAD"), cancellable = true)
    private void rotproxy$blockPing(ServerData data, Runnable save, Runnable pong, EventLoopGroupHolder backend, CallbackInfo ci) {
        if (!ProxyManager.shouldBlockServerConnections()) return;
        data.ping = -1;
        data.players = null;
        data.status = Component.empty();
        data.playerList = List.of(Component.literal("RotProxy is blocking multiplayer traffic."));
        data.motd = ProxyManager.getKillSwitchReasonText();
        data.setState(ServerData.State.UNREACHABLE);
        ci.cancel();
    }

    // Vanilla's pre-1.7 fallback creates its own unproxied Bootstrap. Never allow it.
    @Inject(method = "pingLegacyServer", at = @At("HEAD"), cancellable = true)
    private void rotproxy$blockLegacyFallback(InetSocketAddress address, ServerAddress rawAddress, ServerData data,
                                             EventLoopGroupHolder backend, CallbackInfo ci) {
        data.setState(ServerData.State.UNREACHABLE);
        ci.cancel();
    }
}
