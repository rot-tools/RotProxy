package com.rotproxy;

import com.rotproxy.config.ProxyConfig;
import com.rotproxy.proxy.ProxyManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(value=EnvType.CLIENT)
public class RotProxyMod
implements ClientModInitializer {
    public static final String MOD_ID = "rotproxy";
    public static final Logger LOGGER = LoggerFactory.getLogger((String)"rotproxy");
    private static RotProxyMod INSTANCE;

    public void onInitializeClient() {
        INSTANCE = this;
        ProxyConfig.load();
        ProxyManager.initialize();
        ProxyManager.restoreConfiguredProxy();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ProxyManager.shutdown());
        LOGGER.info("RotProxy initialized.");
    }

    public static RotProxyMod getInstance() {
        return INSTANCE;
    }
}
