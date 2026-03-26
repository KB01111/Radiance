package com.radiance.client.gui;

import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.config.AttributeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

public class ModuleAttributeScreen extends Screen {

    private static final int OK_BORDER = 0xFF34D058;
    private static final int BAD_BORDER = 0xFFE5534B;
    private static final int ROW_LEFT = 20;
    private static final int ROW_RIGHT = 20;
    private static final int WIDGET_WIDTH = 160;
    private static final int VEC3_COMPONENT_WIDTH = 52;
    private static final int VEC3_GAP = 2;
    private static final int HEADER_HEIGHT = 32;
    private static final String MODULE_ATTRIBUTE_SCREEN_NO_ATTRIBUTES = "module_attribute_screen.no_attributes";

    private final Screen parent;
    private final Module module;
    private final List<Row> rows = new ArrayList<>();

    private int scrollY;

    public ModuleAttributeScreen(Screen parent, Module module) {
        super(Text.translatable(module.name));
        this.parent = parent;
        this.module = module;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
            .dimensions(10, 6, 60, 20)
            .build());

        this.rows.clear();

        List<AttributeConfig> attributeConfigs = this.module.attributeConfigs;
        if (attributeConfigs == null || attributeConfigs.isEmpty()) {
            return;
        }

        for (AttributeConfig attributeConfig : attributeConfigs) {
            List<ClickableWidget> widgets = AttributeWidgetUtil.buildWidgets(attributeConfig,
                this.textRenderer, WIDGET_WIDTH, VEC3_COMPONENT_WIDTH);
            for (ClickableWidget widget : widgets) {
                addDrawableChild(widget);
            }
            this.rows.add(new Row(attributeConfig, widgets));
        }
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        context.drawTextWithShadow(this.textRenderer, Text.translatable(this.module.name), 10,
            HEADER_HEIGHT + 8, 0xFFEAEAEA);

        if (this.rows.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer,
                Text.translatable(MODULE_ATTRIBUTE_SCREEN_NO_ATTRIBUTES), 10, 60, 0xFFB0B0B0);
            return;
        }

        int baseY = HEADER_HEIGHT + 28 + this.scrollY;
        int rowHeight = 22;

        for (int i = 0; i < this.rows.size(); i++) {
            Row row = this.rows.get(i);
            int y = baseY + i * rowHeight;
            boolean visible = y >= HEADER_HEIGHT + 18 && y <= this.height - 24;
            if (visible) {
                context.drawTextWithShadow(this.textRenderer, Text.translatable(row.cfg.name),
                    ROW_LEFT, y + 6, 0xFFD0D0D0);
            }

            layoutRowWidgets(row, y);

            String type = row.cfg.type == null ? "" : row.cfg.type.toLowerCase(Locale.ROOT);
            boolean doBorder = AttributeWidgetUtil.shouldValidateBorder(type);

            for (ClickableWidget widget : row.widgets) {
                widget.visible = visible;
                widget.active = visible;

                if (!doBorder || !(widget instanceof TextFieldWidget textFieldWidget)) {
                    continue;
                }

                boolean ok = true;
                if (type.equals("vec3") || type.equals("float")) {
                    ok = AttributeWidgetUtil.isStrictFloat(textFieldWidget.getText());
                } else if (type.equals("int")) {
                    ok = AttributeWidgetUtil.isStrictInt(textFieldWidget.getText());
                }

                AttributeWidgetUtil.drawBorder(context, textFieldWidget.getX(),
                    textFieldWidget.getY(), textFieldWidget.getWidth(), textFieldWidget.getHeight(),
                    ok ? OK_BORDER : BAD_BORDER);
            }
        }
    }

    private void layoutRowWidgets(Row row, int y) {
        int widgetWidth = AttributeWidgetUtil.totalWidgetWidth(row.widgets, WIDGET_WIDTH,
            VEC3_COMPONENT_WIDTH, VEC3_GAP);
        int x = this.width - ROW_RIGHT - widgetWidth;
        AttributeWidgetUtil.layoutWidgets(row.widgets, x, y, WIDGET_WIDTH, VEC3_COMPONENT_WIDTH,
            VEC3_GAP);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int rowHeight = 22;
        int contentHeight = 60 + this.rows.size() * rowHeight + 20;
        int minScroll = Math.min(0, this.height - contentHeight);

        this.scrollY += (int) (amount * 10);
        if (this.scrollY > 0) {
            this.scrollY = 0;
        }
        if (this.scrollY < minScroll) {
            this.scrollY = minScroll;
        }
        return true;
    }

    private static class Row {

        private final AttributeConfig cfg;
        private final List<ClickableWidget> widgets;

        private Row(AttributeConfig cfg, List<ClickableWidget> widgets) {
            this.cfg = cfg;
            this.widgets = widgets;
        }
    }
}
