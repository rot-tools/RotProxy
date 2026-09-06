package com.rotproxy.mixin;

import com.rotproxy.proxy.ProxyManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.proxy.ProxyHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.BandwidthDebugMonitor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Connection.class})
public abstract class ClientConnectionMixin {
    @Inject(method={"configureSerialization(Lio/netty/channel/ChannelPipeline;Lnet/minecraft/network/protocol/PacketFlow;ZLnet/minecraft/network/BandwidthDebugMonitor;)V"}, at={@At(value="TAIL")})
    private static void rotproxy$injectProxyHandler(ChannelPipeline pipeline, PacketFlow side, boolean local, BandwidthDebugMonitor packetSizeLogger, CallbackInfo ci) {
        if (local || side != PacketFlow.CLIENTBOUND || pipeline.get("rotproxy_proxy") != null) {
            return;
        }
        ProxyManager.installGameProxy(pipeline);
    }
}
