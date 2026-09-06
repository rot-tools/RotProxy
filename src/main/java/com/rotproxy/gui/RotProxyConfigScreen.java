package com.rotproxy.gui;

import com.rotproxy.config.ProxyConfig;
import com.rotproxy.config.ProxyProfile;
import com.rotproxy.gui.widget.RotProxyButtonWidget;
import com.rotproxy.gui.widget.RotProxyTextFieldWidget;
import com.rotproxy.proxy.ProxyManager;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Compact layout that also fits Minecraft's default 427 x 240 GUI. */
public class RotProxyConfigScreen extends Screen {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("rotproxy", "icon.png");
    private final Screen parent;
    private int left, top, panelWidth, selected;
    private ProxyProfile draft;
    private RotProxyTextFieldWidget name, host, port, username, password;
    private RotProxyButtonWidget testButton;
    private String feedback = "Apply a proxy to unlock multiplayer.";
    private int feedbackColor = RotProxyTheme.TEXT_MUTED;
    private boolean testing;
    private long testSequence;

    public RotProxyConfigScreen(Screen parent) {
        super(Component.literal("RotProxy"));
        this.parent = parent;
        selected = ProxyConfig.getActiveProfileIndex();
    }

    @Override
    protected void init() {
        if (name != null) captureDraft();
        if (ProxyConfig.getProfiles().isEmpty()) ProxyConfig.addProfile("Profile 1");
        selected = Math.clamp(selected, 0, ProxyConfig.getProfiles().size() - 1);
        if (draft == null) draft = ProxyConfig.getProfiles().get(selected).copy();
        panelWidth = Math.min(520, width - 16);
        left = (width - panelWidth) / 2;
        top = Math.max(2, (height - 236) / 2);
        int x = left + 12, content = panelWidth - 24, half = (content - 8) / 2;
        name = field(x, top + 54, half, "Profile name", draft.name, false);
        button(x + half + 8, top + 54, half, () -> Component.literal("TYPE: " + draft.type), () -> {
            var types = ProxyProfile.ProxyType.values();
            draft.type = types[(draft.type.ordinal() + 1) % types.length];
            invalidateTest();
        }).setTooltip(Tooltip.create(Component.literal("SOCKS5 / HTTP CONNECT / HTTPS (TLS to proxy)")));
        int portWidth = Math.min(80, half);
        host = field(x, top + 86, content - portWidth - 8, "Proxy host", draft.host, false);
        port = field(x + content - portWidth, top + 86, portWidth, "Port", draft.port > 0 ? Integer.toString(draft.port) : "", false);
        port.setMaxLength(5);
        username = field(x, top + 118, half, "Username", draft.username, false);
        password = field(x + half + 8, top + 118, half, "Password", draft.password, true);
        button(left + panelWidth - 58, top + 12, 20, "<", () -> select(-1));
        button(left + panelWidth - 34, top + 12, 20, ">", () -> select(1));
        int actionWidth = (content - 18) / 4;
        button(x, top + 188, actionWidth, "APPLY", this::apply);
        testButton = button(x + actionWidth + 6, top + 188, actionWidth, "TEST", this::test);
        testButton.active = !testing;
        button(x + 2 * (actionWidth + 6), top + 188, actionWidth, "DISABLE", () -> {
            saveDraft();
            ProxyManager.clearProxy();
            feedback("Proxy disabled. Multiplayer blocked.", RotProxyTheme.WARNING);
        });
        button(x + 3 * (actionWidth + 6), top + 188, actionWidth, "CLOSE", this::onClose);
        int small = (content - 18) / 4;
        button(x, top + 212, small, "NEW", () -> {
            saveDraft();
            ProxyConfig.addProfile("Profile " + (ProxyConfig.getProfiles().size() + 1));
            selected = ProxyConfig.getProfiles().size() - 1;
            loadProfile();
        });
        button(x + small + 6, top + 212, small, "CLEAR", () -> {
            ProxyConfig.clearProfile(selected);
            if (selected == ProxyConfig.getActiveProfileIndex()) ProxyManager.clearProxy();
            loadProfile();
        });
        button(x + 2 * (small + 6), top + 212, small, "DELETE", () -> {
            if (ProxyConfig.getProfiles().size() == 1) return;
            if (selected == ProxyConfig.getActiveProfileIndex()) ProxyManager.clearProxy();
            ProxyConfig.removeProfile(selected);
            selected = Math.min(selected, ProxyConfig.getProfiles().size() - 1);
            loadProfile();
        });
        button(x + 3 * (small + 6), top + 212, small, "RESET ALL", () -> {
            ProxyManager.clearProxy();
            ProxyConfig.resetAllProfiles();
            selected = 0;
            loadProfile();
        });
    }

    private RotProxyTextFieldWidget field(int x, int y, int w, String label, String value, boolean secret) {
        var field = new RotProxyTextFieldWidget(font, x, y, w, 20, Component.literal(label));
        field.setMaxLength(256);
        field.setValue(value == null ? "" : value);
        field.setPasswordMode(secret);
        field.setResponder(ignored -> invalidateTest());
        return addRenderableWidget(field);
    }

    private RotProxyButtonWidget button(int x, int y, int w, String label, Runnable action) {
        return button(x, y, w, () -> Component.literal(label), action);
    }

    private RotProxyButtonWidget button(int x, int y, int w, Supplier<Component> label, Runnable action) {
        return addRenderableWidget(new RotProxyButtonWidget(x, y, w, 20, label, ignored -> action.run()));
    }

    private void captureDraft() {
        draft.name = name.getValue().isBlank() ? "Profile " + (selected + 1) : name.getValue().trim();
        draft.host = host.getValue().trim();
        try { draft.port = Integer.parseInt(port.getValue().trim()); }
        catch (NumberFormatException ignored) { draft.port = 0; }
        draft.username = username.getValue();
        draft.password = password.getValue();
    }

    private void saveDraft() {
        captureDraft();
        ProxyConfig.getProfiles().set(selected, draft.copy());
        ProxyConfig.save();
    }

    private void select(int direction) {
        saveDraft();
        selected = Math.floorMod(selected + direction, ProxyConfig.getProfiles().size());
        loadProfile();
    }

    private void loadProfile() {
        invalidateTest();
        draft = ProxyConfig.getProfiles().get(selected).copy();
        name = null;
        ProxyConfig.save();
        rebuildWidgets();
        feedback("Editing " + draft.name + ". APPLY activates this profile.", RotProxyTheme.TEXT_MUTED);
    }

    private void apply() {
        captureDraft();
        if (!draft.isValid()) {
            feedback("Enter a host and a port from 1 to 65535.", RotProxyTheme.ERROR);
            return;
        }
        saveDraft();
        ProxyConfig.setActiveProfileIndex(selected);
        ProxyManager.applyProxy(draft);
        feedback("Verifying proxy. Multiplayer stays blocked.", RotProxyTheme.WARNING);
    }

    private void invalidateTest() {
        testSequence++;
        testing = false;
        if (testButton != null) testButton.active = true;
    }

    private void test() {
        if (testing) return;
        captureDraft();
        if (!draft.isValid()) {
            feedback("Enter a host and a port from 1 to 65535.", RotProxyTheme.ERROR);
            return;
        }
        long sequence = ++testSequence;
        testing = true;
        testButton.active = false;
        feedback("Testing proxy tunnel...", RotProxyTheme.WARNING);
        ProxyManager.testProfile(draft).whenComplete((result, failure) -> minecraft.execute(() -> {
            if (sequence != testSequence) return;
            testing = false;
            testButton.active = true;
            if (failure != null || result == null || !result.success()) {
                feedback("Test failed: " + (result == null ? "connection failed" : result.errorMessage()), RotProxyTheme.ERROR);
            } else {
                feedback("Test OK: " + result.ipAddress() + " / " + result.latencyMs() + " ms. APPLY to activate.", RotProxyTheme.SUCCESS);
            }
        }));
    }

    private void feedback(String value, int color) { feedback = value; feedbackColor = color; }

    @Override
    public void onClose() {
        saveDraft();
        invalidateTest();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, RotProxyTheme.BACKDROP);
        RotProxyTheme.drawPanel(graphics, left, top, panelWidth, 236);
        graphics.blit(ICON, left + 10, top + 6, left + 42, top + 38, 0, 1, 0, 1);
        graphics.text(font, "ROT PROXY", left + 50, top + 11, RotProxyTheme.TEXT);
        graphics.fill(left + 50, top + 26, left + 54, top + 30, RotProxyTheme.VIOLET);
        String profileLabel = (selected + 1) + " / " + ProxyConfig.getProfiles().size() + "  " + draft.name;
        graphics.text(font, font.plainSubstrByWidth(profileLabel, panelWidth - 126), left + 60, top + 24, RotProxyTheme.TEXT_MUTED);
        label(graphics, "PROFILE NAME", name.getX(), top + 43);
        label(graphics, "PROTOCOL", password.getX(), top + 43);
        label(graphics, "PROXY HOST", host.getX(), top + 75);
        label(graphics, "PORT", port.getX(), top + 75);
        label(graphics, "USERNAME (OPTIONAL)", username.getX(), top + 107);
        label(graphics, "PASSWORD (OPTIONAL)", password.getX(), top + 107);
        int content = panelWidth - 24, x = left + 12;
        graphics.fill(x, top + 144, x + content, top + 145, RotProxyTheme.BORDER);
        boolean healthy = ProxyManager.isProxyHealthy();
        int color = healthy ? RotProxyTheme.SUCCESS : ProxyManager.getStatus() == ProxyManager.Status.ERROR ? RotProxyTheme.ERROR : RotProxyTheme.WARNING;
        String status = (healthy ? "PROTECTED" : "BLOCKED") + "  /  " + ProxyManager.getStatus() + "  /  " + ProxyManager.getActiveProfileName();
        graphics.text(font, font.plainSubstrByWidth(status, content), x, top + 150, color);
        String detail = "IP " + ProxyManager.getCurrentIp() + "  |  " + (ProxyManager.getLatencyMs() < 0 ? "--" : ProxyManager.getLatencyMs() + " ms") + "  |  Check every 15s";
        graphics.text(font, font.plainSubstrByWidth(detail, content), x, top + 162, RotProxyTheme.TEXT_DIM);
        String diagnostic = ProxyManager.getLastError().isBlank() ? feedback : "Proxy: " + ProxyManager.getLastError();
        graphics.text(font, font.plainSubstrByWidth(diagnostic, content), x, top + 174, ProxyManager.getLastError().isBlank() ? feedbackColor : RotProxyTheme.ERROR);
        if (mouseX >= x && mouseX <= x + content && mouseY >= top + 172 && mouseY < top + 186) {
            graphics.setTooltipForNextFrame(Component.literal(diagnostic), mouseX, mouseY);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void label(GuiGraphicsExtractor graphics, String value, int x, int y) {
        graphics.text(font, value, x, y, RotProxyTheme.TEXT_MUTED);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
