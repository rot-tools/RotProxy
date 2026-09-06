package com.rotproxy.mixin;

import com.rotproxy.proxy.ProxyManager;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ConnectScreen.class})
public abstract class ConnectScreenMixin {
    @Inject(method={"startConnecting(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/multiplayer/resolver/ServerAddress;Lnet/minecraft/client/multiplayer/ServerData;ZLnet/minecraft/client/multiplayer/TransferState;)V"}, at={@At(value="HEAD")}, cancellable=true)
    private static void rotproxy$blockUnsafeConnect(Screen screen, Minecraft client, ServerAddress address, ServerData info, boolean quickPlay, @Nullable TransferState cookieStorage, CallbackInfo ci) {
        if (!ProxyManager.shouldBlockServerConnections()) {
            return;
        }
        Component reason = ProxyManager.getKillSwitchReasonText();
        SystemToast.addOrUpdate((ToastManager)client.gui.toastManager(), (SystemToast.SystemToastId)SystemToast.SystemToastId.PERIODIC_NOTIFICATION, (Component)Component.literal((String)"RotProxy Kill Switch"), (Component)reason);
        client.gui.setScreen((Screen)new DisconnectedScreen(screen, (Component)Component.literal((String)"RotProxy Kill Switch"), reason));
        ci.cancel();
    }
}
