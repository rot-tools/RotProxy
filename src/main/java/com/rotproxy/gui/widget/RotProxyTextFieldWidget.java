package com.rotproxy.gui.widget;

import com.rotproxy.gui.RotProxyTheme;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.FormattedCharSequence;

public class RotProxyTextFieldWidget
extends EditBox {
    private boolean passwordMode;

    public RotProxyTextFieldWidget(Font textRenderer, int x, int y, int width, int height, Component text) {
        super(textRenderer, x, y, width, height, text);
        this.setBordered(false);
        this.setTextColor(RotProxyTheme.TEXT);
        this.setTextColorUneditable(RotProxyTheme.TEXT_MUTED);
    }

    public void setPasswordMode(boolean passwordMode) {
        if (passwordMode && !this.passwordMode) {
            this.addFormatter((text, firstCharacterIndex) -> FormattedCharSequence.forward((String)RotProxyTextFieldWidget.mask(text, firstCharacterIndex), (Style)Style.EMPTY));
        }
        this.passwordMode = passwordMode;
    }

    public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        RotProxyTheme.drawInset(context, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.isFocused());
        super.extractWidgetRenderState(context, mouseX, mouseY, delta);
    }

    private static String mask(String text, int firstCharacterIndex) {
        return "*".repeat(text.length());
    }
}
