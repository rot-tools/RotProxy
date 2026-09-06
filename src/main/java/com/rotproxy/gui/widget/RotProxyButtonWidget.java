package com.rotproxy.gui.widget;

import com.rotproxy.gui.RotProxyTheme;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class RotProxyButtonWidget extends AbstractWidget {
    private final Supplier<Component> labelSupplier;
    private final PressAction pressAction;

    public RotProxyButtonWidget(int x, int y, int width, int height, Component label, PressAction action) {
        this(x, y, width, height, () -> label, action);
    }

    public RotProxyButtonWidget(int x, int y, int width, int height, Supplier<Component> label, PressAction action) {
        super(x, y, width, height, label.get());
        this.labelSupplier = label;
        this.pressAction = action;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        setMessage(labelSupplier.get());
        boolean highlighted = isHoveredOrFocused();
        int fill = !active ? RotProxyTheme.BUTTON_DISABLED : highlighted ? RotProxyTheme.BUTTON_HOVER : RotProxyTheme.BUTTON;
        graphics.fill(getX(), getY(), getRight(), getBottom(), fill);
        graphics.outline(getX(), getY(), width, height, highlighted && active ? RotProxyTheme.BORDER_BRIGHT : RotProxyTheme.BORDER);
        var font = Minecraft.getInstance().font;
        graphics.centeredText(font, font.plainSubstrByWidth(getMessage().getString(), width - 8),
                getX() + width / 2, getY() + (height - 8) / 2, active ? RotProxyTheme.TEXT : RotProxyTheme.TEXT_MUTED);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (active && visible) pressAction.onPress(this);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (active && visible && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER || event.key() == GLFW.GLFW_KEY_SPACE)) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            pressAction.onPress(this);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }

    @FunctionalInterface
    public interface PressAction { void onPress(RotProxyButtonWidget button); }
}
