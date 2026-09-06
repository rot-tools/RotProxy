package com.rotproxy.mixin;

import com.rotproxy.gui.RotProxyConfigScreen;
import com.rotproxy.gui.widget.RotProxyButtonWidget;
import com.rotproxy.proxy.ProxyManager;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={JoinMultiplayerScreen.class})
public abstract class RotProxyMultiplayerScreenMixin
extends Screen {
    @Shadow
    private Button selectButton;
    @Unique
    private RotProxyButtonWidget rotproxy$button;
    @Unique
    private boolean rotproxy$lastBlockingState;
    @Unique
    private boolean rotproxy$toastShown;

    @Shadow
    protected abstract void onSelectedChange();

    protected RotProxyMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method={"init"}, at={@At(value="TAIL")})
    private void rotproxy$addButton(CallbackInfo ci) {
        JoinMultiplayerScreen self = (JoinMultiplayerScreen)(Object)this;
        this.rotproxy$button = new RotProxyButtonWidget(this.width - 154, 8, 146, 20, this::rotproxy$buildLabel, button -> {
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen((Screen)new RotProxyConfigScreen((Screen)self));
            }
        });
        this.addRenderableWidget(this.rotproxy$button);
        if (this.minecraft != null && ProxyManager.shouldBlockServerConnections() && !this.rotproxy$toastShown) {
            this.rotproxy$toastShown = true;
            SystemToast.addOrUpdate((ToastManager)this.minecraft.gui.toastManager(), (SystemToast.SystemToastId)SystemToast.SystemToastId.PERIODIC_NOTIFICATION, (Component)Component.literal((String)"RotProxy Kill Switch"), (Component)Component.literal((String)"Multiplayer traffic is blocked until your proxy becomes healthy."));
        }
    }

    @Inject(method={"tick"}, at={@At(value="TAIL")})
    private void rotproxy$syncKillSwitchState(CallbackInfo ci) {
        boolean blocking = ProxyManager.shouldBlockServerConnections();
        if (this.selectButton != null && blocking) {
            this.selectButton.active = false;
        }
        if (!blocking && this.rotproxy$lastBlockingState) {
            this.onSelectedChange();
            ServerList servers = ((JoinMultiplayerScreen)(Object)this).getServers();
            if (servers != null) {
                for (int index = 0; index < servers.size(); index++) {
                    servers.get(index).setState(ServerData.State.INITIAL);
                }
            }
        }
        if (blocking == this.rotproxy$lastBlockingState) {
            return;
        }
        this.rotproxy$lastBlockingState = blocking;
        if (!blocking) {
            return;
        }
        JoinMultiplayerScreen self = (JoinMultiplayerScreen)(Object)this;
        self.getPinger().removeAll();
        ServerList serverList = self.getServers();
        if (serverList == null) {
            return;
        }
        for (int index = 0; index < serverList.size(); ++index) {
            ServerData server = serverList.get(index);
            server.ping = -1L;
            server.players = null;
            server.status = Component.empty();
            server.playerList = List.of(Component.literal((String)"RotProxy kill switch is blocking multiplayer traffic."));
            server.motd = Component.literal((String)"Blocked by RotProxy until a healthy proxy is active.");
            server.setState(ServerData.State.UNREACHABLE);
        }
    }

    @Unique
    private Component rotproxy$buildLabel() {
        ProxyManager.Status status = ProxyManager.getStatus();
        return switch (status) {
            default -> throw new MatchException(null, null);
            case ProxyManager.Status.CONNECTED -> Component.literal((String)"RotProxy: Protected");
            case ProxyManager.Status.CONNECTING -> Component.literal((String)"RotProxy: Blocking");
            case ProxyManager.Status.ERROR -> Component.literal((String)"RotProxy: Blocked");
            case ProxyManager.Status.DISABLED -> Component.literal((String)"RotProxy: Blocked");
        };
    }
}
