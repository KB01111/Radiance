package com.radiance.client.gui;

import com.radiance.Radiance;
import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.ModuleEntry;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.Presets;
import com.radiance.client.pipeline.config.AttributeConfig;
import com.radiance.client.pipeline.config.ImageConfig;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IDrawContextExt;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class RenderPipelineScreen extends Screen {

    private static final Identifier GEAR_TEX = Identifier.of(Radiance.MOD_ID,
        "textures/gui/render_pipeline/gear.png");
    private static final Map<String, Integer> FORMAT_COLORS = Map.of("R8G8B8A8_SRGB", 0xFF4EA5FF,
        "R8G8B8A8_UNORM", 0xFF62E36A, "R16G16_SFLOAT", 0xFFFF6BD6, "R16_SFLOAT", 0xFF6BE6FF,
        "R16G16B16A16_SFLOAT", 0xFFFFB84E);
    private static final int HEADER_HEIGHT = 32;
    private static final float GLOBAL_SCALE = 0.75f;
    private static final String RENDER_PIPELINE_SCREEN_BACK = "render_pipeline_screen.back";
    private static final String RENDER_PIPELINE_SCREEN_SAVE_AND_BUILD = "render_pipeline_screen.save_and_build";
    private static final String RENDER_PIPELINE_SCREEN_RELOAD = "render_pipeline_screen.reload";
    private static final String RENDER_PIPELINE_SCREEN_ADD_MODULE = "render_pipeline_screen.add_module";
    private static final String RENDER_PIPELINE_SCREEN_BACK_HINT = "render_pipeline_screen.back_hint";
    private static final String RENDER_PIPELINE_PRESET_NAME = "render_pipeline.preset.name";
    private static final String RENDER_PIPELINE_MODE_NAME = "render_pipeline.mode.name";

    private final Screen parent;
    private final List<ModuleNode> nodes = new ArrayList<>();
    private final List<ModuleConnection> moduleConnections = new ArrayList<>();
    private final List<PresetEntry> presets = new ArrayList<>();
    private final List<PresetModuleBlock> presetBlocks = new ArrayList<>();
    private final List<ClickableWidget> presetWidgets = new ArrayList<>();

    private ModuleNode draggedNode;
    private double lastMouseX;
    private double lastMouseY;
    private ModuleSelector activeSelector;
    private ImageConfig localFinalOutput;
    private ImageConfig pendingPort;
    private boolean isPendingOutput;
    private boolean isPanning;

    private Mode mode = Mode.PIPELINE;
    private PresetEntry activePreset;
    private PresetSelector activePresetSelector;
    private int presetScrollY;
    private ButtonWidget secondaryBtn;

    public RenderPipelineScreen(Screen parent) {
        super(Text.literal("Render Pipeline"));
        this.parent = parent;
        registerDefaultPresets();

        Pipeline.PipelineMode pipelineMode = Pipeline.getPipelineMode();
        this.mode = pipelineMode == Pipeline.PipelineMode.PRESET ? Mode.PRESET : Mode.PIPELINE;

        String presetName = Pipeline.processPresetName(Pipeline.getActivePreset());
        if (presetName != null) {
            for (PresetEntry entry : this.presets) {
                if (Objects.equals(entry.name(), presetName)) {
                    this.activePreset = entry;
                    break;
                }
            }
        }

        if (this.mode == Mode.PRESET && this.activePreset == null && !this.presets.isEmpty()) {
            this.activePreset = this.presets.get(0);
        }
    }

    @Override
    protected void init() {
        rebuildUI();
    }

    private void rebuildUI() {
        clearChildren();
        this.activeSelector = null;
        this.activePresetSelector = null;
        this.presetWidgets.clear();

        if (this.mode == Mode.PRESET && this.activePreset == null && !this.presets.isEmpty()) {
            this.activePreset = this.presets.get(0);
        }

        if (this.mode == Mode.PIPELINE) {
            refreshPipeline();
        }

        int backX = 10;
        int backW = 60;
        int toggleX = backX + backW + 5;
        int toggleW = 110;
        int secondaryX = toggleX + toggleW + 5;
        int secondaryW = 150;

        addDrawableChild(
            ButtonWidget.builder(Text.translatable(RENDER_PIPELINE_SCREEN_BACK), button -> close())
                .dimensions(backX, 6, backW, 20).build());

        addDrawableChild(ButtonWidget.builder(
            Text.translatable(RENDER_PIPELINE_MODE_NAME)
                .append(Text.literal(": "))
                .append(Text.translatable(this.mode.key)),
            button -> {
                if (this.mode == Mode.PIPELINE) {
                    if (this.activePreset == null && !this.presets.isEmpty()) {
                        this.activePreset = this.presets.get(0);
                    }
                    Pipeline.switchToPresetMode(
                        this.activePreset != null ? this.activePreset.name() : null);
                    this.mode = Mode.PRESET;
                } else {
                    Pipeline.switchToPipelineMode();
                    this.mode = Mode.PIPELINE;
                }
                rebuildUI();
            }).dimensions(toggleX, 6, toggleW, 20).build());

        if (this.mode == Mode.PIPELINE) {
            this.secondaryBtn = addDrawableChild(
                ButtonWidget.builder(Text.translatable(RENDER_PIPELINE_SCREEN_ADD_MODULE),
                    button -> {
                        Map<String, ModuleEntry> entries = Pipeline.INSTANCE.getModuleEntries();
                        if (entries != null && !entries.isEmpty()) {
                            this.activeSelector = new ModuleSelector(secondaryX,
                                HEADER_HEIGHT + 4, entries);
                        }
                    }).dimensions(secondaryX, 6, secondaryW, 20).build());
        } else {
            Text activePresetText = this.activePreset != null
                ? Text.translatable(this.activePreset.name())
                : Text.literal("N/A");
            this.secondaryBtn = addDrawableChild(ButtonWidget.builder(
                Text.translatable(RENDER_PIPELINE_PRESET_NAME)
                    .append(Text.literal(": "))
                    .append(activePresetText), button -> {
                    if (!this.presets.isEmpty()) {
                        this.activePresetSelector = new PresetSelector(secondaryX,
                            HEADER_HEIGHT + 4, this.presets);
                    }
                }).dimensions(secondaryX, 6, secondaryW, 20).build());
        }

        addDrawableChild(
            ButtonWidget.builder(Text.translatable(RENDER_PIPELINE_SCREEN_SAVE_AND_BUILD),
                button -> {
                    if (this.mode == Mode.PIPELINE) {
                        syncToPipeline();
                    } else {
                        syncPresetToPipeline();
                    }
                }).dimensions(secondaryX + secondaryW + 5, 6, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable(RENDER_PIPELINE_SCREEN_RELOAD),
            button -> {
                if (this.mode == Mode.PIPELINE) {
                    refreshPipeline();
                } else {
                    applyActivePreset();
                }
            }).dimensions(secondaryX + secondaryW + 110, 6, 100, 20).build());

        if (this.mode == Mode.PRESET) {
            this.presetScrollY = 0;
            if (this.activePreset == null && !this.presets.isEmpty()) {
                this.activePreset = this.presets.get(0);
            }
            applyActivePreset();
        }
    }

    public void refreshPipeline() {
        this.nodes.clear();
        this.moduleConnections.clear();
        this.pendingPort = null;
        this.localFinalOutput = null;

        for (Module module : Pipeline.INSTANCE.getModules()) {
            this.nodes.add(new ModuleNode(module));
        }

        Pipeline.INSTANCE.getModuleConnections().forEach((src, list) -> list.forEach(
            dst -> this.moduleConnections.add(new ModuleConnection(src, dst))));

        for (ModuleNode node : this.nodes) {
            for (ImageConfig outputImageConfig : node.module.outputImageConfigs) {
                if (outputImageConfig.finalOutput) {
                    this.localFinalOutput = outputImageConfig;
                    break;
                }
            }
        }
    }

    public void syncToPipeline() {
        Pipeline.clear();
        for (ModuleNode node : this.nodes) {
            Pipeline.addModule(node.module);
        }

        for (ModuleConnection moduleConnection : this.moduleConnections) {
            Pipeline.connect(moduleConnection.src, moduleConnection.dst);
        }

        Pipeline.build();
        refreshPipeline();
    }

    @Override
    public void close() {
        if (this.mode == Mode.PIPELINE) {
            syncToPipeline();
        } else {
            syncPresetToPipeline();
        }
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        for (ModuleNode node : this.nodes) {
            node.updateWidth(this.textRenderer);
        }

        this.renderBackground(context);

        context.getMatrices().push();
        context.getMatrices().scale(GLOBAL_SCALE, GLOBAL_SCALE, 1.0f);

        int scaledMouseX = (int) (mouseX / GLOBAL_SCALE);
        int scaledMouseY = (int) (mouseY / GLOBAL_SCALE);

        super.render(context, scaledMouseX, scaledMouseY, delta);

        context.drawTextWithShadow(this.textRenderer, Text.translatable(
            RENDER_PIPELINE_SCREEN_BACK_HINT), 10, HEADER_HEIGHT + 8, 0xFFEAEAEA);

        if (this.mode == Mode.PIPELINE) {
            for (ModuleNode node : this.nodes) {
                drawModuleNode(context, node);
            }

            drawConnections(context);

            if (this.activeSelector != null) {
                context.getMatrices().push();
                context.getMatrices().translate(0.0f, 0.0f, 200.0f);
                this.activeSelector.render(context, scaledMouseX, scaledMouseY);
                context.getMatrices().pop();
            }
        } else {
            renderPresetMode(context);
            if (this.activePresetSelector != null) {
                context.getMatrices().push();
                context.getMatrices().translate(0.0f, 0.0f, 200.0f);
                this.activePresetSelector.render(context, scaledMouseX, scaledMouseY);
                context.getMatrices().pop();
            }
        }

        context.getMatrices().pop();
    }

    private void renderPresetMode(DrawContext context) {
        int scaledWidth = scaledW();
        int scaledHeight = scaledH();
        int left = 10;
        int top = HEADER_HEIGHT + 28;
        int contentWidth = scaledWidth - 20;
        int y = top + this.presetScrollY;
        int titleGap = 12;
        int afterTitle = 8;
        int rowHeight = 22;

        for (PresetModuleBlock block : this.presetBlocks) {
            if (block.rows.isEmpty()) {
                continue;
            }

            int titleY = y;
            int titleWidth = this.textRenderer.getWidth(Text.translatable(block.module.name));
            int titleX = left + (contentWidth - titleWidth) / 2;
            boolean titleVisible = titleY >= HEADER_HEIGHT + 18 && titleY <= scaledHeight - 24;
            if (titleVisible) {
                context.drawTextWithShadow(this.textRenderer, Text.translatable(block.module.name),
                    titleX, titleY, 0xFFEAEAEA);
            }

            y += titleGap + afterTitle;

            for (int i = 0; i < block.rows.size(); i++) {
                PresetRow row = block.rows.get(i);
                int rowY = y + i * rowHeight;
                boolean visible = rowY >= HEADER_HEIGHT + 18 && rowY <= scaledHeight - 24;

                if (visible) {
                    context.drawTextWithShadow(this.textRenderer, Text.translatable(row.cfg.name),
                        left + 10, rowY + 6, 0xFFD0D0D0);
                }

                layoutPresetRowWidgets(row, left + contentWidth - 10, rowY);

                String type = row.cfg.type == null ? "" : row.cfg.type.toLowerCase(Locale.ROOT);
                boolean doBorder = AttributeWidgetUtil.shouldValidateBorder(type);

                for (ClickableWidget widget : row.widgets) {
                    widget.visible = visible;
                    widget.active = visible;

                    if (!doBorder) {
                        continue;
                    }

                    boolean ok = true;
                    if (type.equals("vec3")) {
                        if (widget instanceof TextFieldWidget textFieldWidget) {
                            ok = AttributeWidgetUtil.isStrictFloat(textFieldWidget.getText());
                        }
                    } else if (type.equals("int")) {
                        if (widget instanceof TextFieldWidget textFieldWidget) {
                            ok = AttributeWidgetUtil.isStrictInt(textFieldWidget.getText());
                        }
                    } else if (type.equals("float")
                        && widget instanceof TextFieldWidget textFieldWidget) {
                        ok = AttributeWidgetUtil.isStrictFloat(textFieldWidget.getText());
                    }

                    AttributeWidgetUtil.drawBorder(context, widget.getX() - 1, widget.getY() - 1,
                        widget.getWidth() + 2, widget.getHeight() + 2,
                        ok ? 0xFF34D058 : 0xFFE5534B);
                }
            }

            y += block.rows.size() * rowHeight + 18;
        }
    }

    private void layoutPresetRowWidgets(PresetRow row, int rightEdge, int y) {
        int singleWidth = 200;
        int tripleWidth = 64;
        int gap = 4;
        int widgetWidth = AttributeWidgetUtil.totalWidgetWidth(row.widgets, singleWidth,
            tripleWidth, gap);
        int x = rightEdge - widgetWidth;
        AttributeWidgetUtil.layoutWidgets(row.widgets, x, y, singleWidth, tripleWidth, gap);
    }

    private int scaledW() {
        return (int) (this.width / GLOBAL_SCALE);
    }

    private int scaledH() {
        return (int) (this.height / GLOBAL_SCALE);
    }

    private void drawConnections(DrawContext context) {
        for (ModuleConnection link : this.moduleConnections) {
            PortPos source = getPortPosition(link.src, true);
            PortPos target = getPortPosition(link.dst, false);
            if (source != null && target != null) {
                drawBezier(context, source.x, source.y, target.x, target.y,
                    getFormatColor(link.src.format));
            }
        }
    }

    private PortPos getPortPosition(ImageConfig config, boolean isOutput) {
        for (ModuleNode node : this.nodes) {
            if (node.module != config.owner) {
                continue;
            }

            List<ImageConfig> configs = isOutput ? node.module.outputImageConfigs
                : node.module.inputImageConfigs;
            if (configs == null) {
                return null;
            }

            int index = configs.indexOf(config);
            if (index == -1) {
                return null;
            }

            int x = (int) node.module.x;
            int y = (int) node.module.y + HEADER_HEIGHT;
            int rowY = y + node.headerH + node.pad + index * node.rowH + 7;
            int dotY = rowY + 7;
            int dotX = isOutput ? x + node.width - 10 : x + 10;
            return new PortPos(dotX, dotY);
        }

        return null;
    }

    private void drawBezier(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        int segments = 32;
        float previousX = x1;
        float previousY = y1;
        float thickness = 1.2f;
        float controlOffset = Math.abs(x2 - x1) * 0.5f;

        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            float invT = 1.0f - t;
            float b0 = invT * invT * invT;
            float b1 = 3.0f * invT * invT * t;
            float b2 = 3.0f * invT * t * t;
            float b3 = t * t * t;

            float currentX = b0 * x1 + b1 * (x1 + controlOffset) + b2 * (x2 - controlOffset)
                + b3 * x2;
            float currentY = b0 * y1 + b1 * y1 + b2 * y2 + b3 * y2;

            ((IDrawContextExt) (Object) context).radiance$drawOrientedQuad(RenderLayer.getGui(),
                previousX, previousY, currentX, currentY, thickness, color);

            previousX = currentX;
            previousY = currentY;
        }
    }

    private void drawModuleNode(DrawContext context, ModuleNode moduleNode) {
        int x = (int) moduleNode.module.x;
        int y = (int) moduleNode.module.y + HEADER_HEIGHT;
        int width = moduleNode.width;
        int height = moduleNode.height();

        context.fill(x, y, x + width, y + height, 0xFF20242C);
        context.fill(x, y, x + width, y + moduleNode.headerH, 0xFF2B3240);
        context.drawTextWithShadow(this.textRenderer, Text.translatable(moduleNode.module.name),
            x + 6, y + 5, 0xFFEAEAEA);

        int buttonSize = 12;
        int deleteX = x + width - buttonSize - 4;
        int buttonY = y + (moduleNode.headerH - buttonSize) / 2;
        int gearX = deleteX - buttonSize - 2;

        context.drawTexture(GEAR_TEX, gearX, buttonY, 0, 0.0f, 0.0f, buttonSize, buttonSize,
            buttonSize, buttonSize);
        context.drawTextWithShadow(this.textRenderer, "x", deleteX + 3, buttonY + 2, 0xFFFF5A5A);

        for (int i = 0; i < moduleNode.rows(); i++) {
            int rowY = y + moduleNode.headerH + moduleNode.pad + i * moduleNode.rowH + 7;

            if (i < moduleNode.module.inputImageConfigs.size()) {
                ImageConfig input = moduleNode.module.inputImageConfigs.get(i);
                int dotX = x + 10;
                int dotY = rowY + 7;
                int color = input == this.pendingPort ? 0xFFFFFF00 : getFormatColor(input.format);
                boolean isConnected = this.moduleConnections.stream().anyMatch(
                    link -> link.dst == input);
                drawPortDot(context, dotX, dotY, color, isConnected, false);
                context.drawTextWithShadow(this.textRenderer, input.name, x + 18, rowY + 2,
                    0xFFD0D0D0);
            }

            if (i < moduleNode.module.outputImageConfigs.size()) {
                ImageConfig output = moduleNode.module.outputImageConfigs.get(i);
                int dotX = x + width - 10;
                int dotY = rowY + 7;
                int color = output == this.pendingPort ? 0xFFFFFF00
                    : getFormatColor(output.format);
                boolean isConnected = this.moduleConnections.stream().anyMatch(
                    link -> link.src == output);
                drawPortDot(context, dotX, dotY, color, isConnected,
                    output == this.localFinalOutput);

                int nameWidth = this.textRenderer.getWidth(output.name);
                context.drawTextWithShadow(this.textRenderer, output.name, dotX - 8 - nameWidth,
                    rowY + 2, 0xFFD0D0D0);
            }
        }
    }

    private void drawPortDot(DrawContext context, int centerX, int centerY, int color,
        boolean filled, boolean isFinal) {
        context.fill(centerX - 4, centerY - 4, centerX + 5, centerY + 5,
            isFinal ? 0xFF55FF55 : 0xFF000000);
        context.fill(centerX - 3, centerY - 3, centerX + 4, centerY + 4, 0xFF000000);
        context.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, color);
        if (!filled) {
            context.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, 0xFF20242C);
        }
    }

    private boolean testCycle(ImageConfig source, ImageConfig target) {
        if (source.owner == target.owner) {
            return true;
        }
        return hasPath(target.owner, source.owner);
    }

    private boolean hasPath(Module start, Module target) {
        if (start == target) {
            return true;
        }

        for (ModuleConnection link : this.moduleConnections) {
            if (link.src.owner == start && hasPath(link.dst.owner, target)) {
                return true;
            }
        }

        return false;
    }

    private void handleLocalConnection(ImageConfig current, boolean isOutput) {
        if (this.pendingPort == null) {
            this.pendingPort = current;
            this.isPendingOutput = isOutput;
            return;
        }

        if (this.isPendingOutput != isOutput) {
            ImageConfig source = this.isPendingOutput ? this.pendingPort : current;
            ImageConfig target = this.isPendingOutput ? current : this.pendingPort;

            if (!Objects.equals(source.format, target.format) || source.owner == target.owner) {
                this.pendingPort = null;
                return;
            }

            if (!testCycle(source, target)) {
                this.moduleConnections.removeIf(link -> link.dst == target);
                this.moduleConnections.add(new ModuleConnection(source, target));
            }
        }

        this.pendingPort = null;
    }

    private void deleteNode(ModuleNode node) {
        this.nodes.remove(node);
        this.moduleConnections.removeIf(
            link -> link.src.owner == node.module || link.dst.owner == node.module);

        if (this.draggedNode == node) {
            this.draggedNode = null;
        }
        if (this.pendingPort != null && this.pendingPort.owner == node.module) {
            this.pendingPort = null;
        }
        if (this.localFinalOutput != null && this.localFinalOutput.owner == node.module) {
            this.localFinalOutput = null;
        }

        this.activeSelector = null;
    }

    private boolean isGearClicked(ModuleNode node, double mouseX, double mouseY) {
        int x = (int) node.module.x;
        int y = (int) node.module.y + HEADER_HEIGHT;
        int buttonSize = 12;
        int deleteX = x + node.width - buttonSize - 4;
        int buttonY = y + (node.headerH - buttonSize) / 2;
        int gearX = deleteX - buttonSize - 2;
        return mouseX >= gearX && mouseX <= gearX + buttonSize && mouseY >= buttonY
            && mouseY <= buttonY + buttonSize;
    }

    private boolean isDeleteClicked(ModuleNode node, double mouseX, double mouseY) {
        int x = (int) node.module.x;
        int y = (int) node.module.y + HEADER_HEIGHT;
        int buttonSize = 12;
        int deleteX = x + node.width - buttonSize - 4;
        int buttonY = y + (node.headerH - buttonSize) / 2;
        return mouseX >= deleteX && mouseX <= deleteX + buttonSize && mouseY >= buttonY
            && mouseY <= buttonY + buttonSize;
    }

    private ImageConfig getClickedPort(ModuleNode node, double mouseX, double mouseY,
        boolean isOutput) {
        int x = (int) node.module.x;
        int y = (int) node.module.y + HEADER_HEIGHT;
        int width = node.width;
        List<ImageConfig> configs = isOutput ? node.module.outputImageConfigs
            : node.module.inputImageConfigs;
        if (configs == null) {
            return null;
        }

        for (int i = 0; i < configs.size(); i++) {
            int rowY = y + node.headerH + node.pad + i * node.rowH + 7;
            int dotY = rowY + 7;
            int dotX = isOutput ? x + width - 10 : x + 10;
            if (Math.abs(mouseX - dotX) <= 8 && Math.abs(mouseY - dotY) <= 8) {
                return configs.get(i);
            }
        }

        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX /= GLOBAL_SCALE;
        mouseY /= GLOBAL_SCALE;

        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.isPanning = false;
        this.draggedNode = null;

        if (mouseY < HEADER_HEIGHT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (this.mode == Mode.PRESET) {
            if (this.activePresetSelector != null) {
                if (this.activePresetSelector.onClick(mouseX, mouseY)) {
                    this.activePresetSelector = null;
                    return true;
                }
                this.activePresetSelector = null;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        for (ModuleNode node : this.nodes) {
            if (button == 0 && isDeleteClicked(node, mouseX, mouseY)) {
                deleteNode(node);
                return true;
            }

            if (button == 0 && isGearClicked(node, mouseX, mouseY)) {
                MinecraftClient.getInstance().setScreen(new ModuleAttributeScreen(this, node.module));
                return true;
            }
        }

        for (ModuleNode node : this.nodes) {
            ImageConfig clickedInput = getClickedPort(node, mouseX, mouseY, false);
            ImageConfig clickedOutput = getClickedPort(node, mouseX, mouseY, true);
            ImageConfig current = clickedInput != null ? clickedInput : clickedOutput;

            if (current == null) {
                continue;
            }

            if (button == 0) {
                handleLocalConnection(current, clickedOutput != null);
                return true;
            }

            if (button == 1) {
                boolean isConnected = this.moduleConnections.stream().anyMatch(
                    link -> link.src == current || link.dst == current);
                if (isConnected) {
                    this.moduleConnections.removeIf(
                        link -> link.src == current || link.dst == current);
                } else if (clickedOutput != null) {
                    if (this.localFinalOutput != null && this.localFinalOutput != current) {
                        this.localFinalOutput.finalOutput = false;
                    }

                    this.localFinalOutput = this.localFinalOutput == current ? null : current;
                    current.finalOutput = this.localFinalOutput == current;
                }
                return true;
            }
        }

        if (this.activeSelector != null) {
            if (this.activeSelector.onClick(mouseX, mouseY)) {
                this.activeSelector = null;
                return true;
            }
            this.activeSelector = null;
        }

        for (int i = this.nodes.size() - 1; i >= 0; i--) {
            ModuleNode node = this.nodes.get(i);
            if (mouseX >= node.module.x && mouseX <= node.module.x + node.width && mouseY >= (
                node.module.y + HEADER_HEIGHT) && mouseY <= (
                node.module.y + HEADER_HEIGHT + node.height())) {
                if (button == 0) {
                    this.draggedNode = node;
                } else if (button == 1) {
                    this.isPanning = true;
                }
                return true;
            }
        }

        if (button == 0 || button == 1) {
            this.isPanning = true;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (this.mode != Mode.PRESET) {
            return super.mouseScrolled(mouseX, mouseY, amount);
        }

        int scaledHeight = scaledH();
        int top = HEADER_HEIGHT + 28;
        int rowHeight = 22;
        int contentHeight = top + this.presetBlocks.stream().filter(block -> !block.rows.isEmpty())
            .mapToInt(block -> 12 + 8 + block.rows.size() * rowHeight + 18).sum();
        int minScroll = Math.min(0, scaledHeight - contentHeight - 10);

        this.presetScrollY += (int) (amount * 12);
        if (this.presetScrollY > 0) {
            this.presetScrollY = 0;
        }
        if (this.presetScrollY < minScroll) {
            this.presetScrollY = minScroll;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX,
        double deltaY) {
        mouseX /= GLOBAL_SCALE;
        mouseY /= GLOBAL_SCALE;

        if (this.mode == Mode.PRESET) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX / GLOBAL_SCALE,
                deltaY / GLOBAL_SCALE);
        }

        if (super.mouseDragged(mouseX, mouseY, button, deltaX / GLOBAL_SCALE,
            deltaY / GLOBAL_SCALE)) {
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            return true;
        }

        double deltaMouseX = mouseX - this.lastMouseX;
        double deltaMouseY = mouseY - this.lastMouseY;

        if (button == 0 && this.draggedNode != null) {
            this.draggedNode.module.x += deltaMouseX;
            this.draggedNode.module.y += deltaMouseY;
        } else if (this.isPanning && (button == 0 || button == 1)) {
            for (ModuleNode node : this.nodes) {
                node.module.x += deltaMouseX;
                node.module.y += deltaMouseY;
            }
        }

        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseX /= GLOBAL_SCALE;
        mouseY /= GLOBAL_SCALE;

        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        this.draggedNode = null;
        this.isPanning = false;
        return handled;
    }

    private int getFormatColor(String format) {
        Integer color = FORMAT_COLORS.get(format);
        if (color == null) {
            throw new RuntimeException("No color for image format: " + format);
        }
        return color;
    }

    private void registerDefaultPresets() {
        this.presets.clear();
        if (Pipeline.isPresetAvailable(Presets.RT_DLSSRR.key)) {
            this.presets.add(new PresetEntry(Presets.RT_DLSSRR.key));
        }
        if (Pipeline.isPresetAvailable(Presets.RT_NRD.key)) {
            this.presets.add(new PresetEntry(Presets.RT_NRD.key));
        }
        if (Pipeline.isPresetAvailable(Presets.RT_NRD_FSR.key)) {
            this.presets.add(new PresetEntry(Presets.RT_NRD_FSR.key));
        }
        if (Pipeline.isPresetAvailable(Presets.RT_NRD_XESS.key)) {
            this.presets.add(new PresetEntry(Presets.RT_NRD_XESS.key));
        }
    }

    private void applyActivePreset() {
        this.presetBlocks.clear();
        for (ClickableWidget widget : this.presetWidgets) {
            remove(widget);
        }
        this.presetWidgets.clear();

        if (this.activePreset == null) {
            return;
        }

        Pipeline.switchToPresetMode(this.activePreset.name());
        List<Module> modules = new ArrayList<>(Pipeline.INSTANCE.getModules());
        for (Module module : modules) {
            PresetModuleBlock block = new PresetModuleBlock(module);
            this.presetBlocks.add(block);
            if (module.attributeConfigs == null || module.attributeConfigs.isEmpty()) {
                continue;
            }

            for (AttributeConfig attributeConfig : module.attributeConfigs) {
                List<ClickableWidget> widgets = buildPresetWidgets(attributeConfig);
                for (ClickableWidget widget : widgets) {
                    this.presetWidgets.add(addDrawableChild(widget));
                }
                block.rows.add(new PresetRow(attributeConfig, widgets));
            }
        }

        if (this.secondaryBtn != null && this.mode == Mode.PRESET) {
            Text activePresetText = this.activePreset != null
                ? Text.translatable(this.activePreset.name())
                : Text.literal("N/A");
            this.secondaryBtn.setMessage(Text.translatable(RENDER_PIPELINE_PRESET_NAME)
                .append(Text.literal(": "))
                .append(activePresetText));
        }
    }

    private void syncPresetToPipeline() {
        Pipeline.savePipeline();
        Pipeline.build();
        refreshPipeline();
    }

    private List<ClickableWidget> buildPresetWidgets(AttributeConfig cfg) {
        return AttributeWidgetUtil.buildWidgets(cfg, this.textRenderer, 200, 64);
    }

    private enum Mode {
        PIPELINE("render_pipeline.mode.pipeline"),
        PRESET("render_pipeline.mode.preset");

        private final String key;

        Mode(String key) {
            this.key = key;
        }
    }

    private record ModuleConnection(ImageConfig src, ImageConfig dst) {
    }

    private record PortPos(int x, int y) {
    }

    private record PresetEntry(String name) {
    }

    private record PresetRow(AttributeConfig cfg, List<ClickableWidget> widgets) {
    }

    private static class PresetModuleBlock {

        private final Module module;
        private final List<PresetRow> rows = new ArrayList<>();

        private PresetModuleBlock(Module module) {
            this.module = module;
        }
    }

    private class ModuleSelector {

        private final int x;
        private final int y;
        private final int width;
        private final List<ModuleEntry> options;
        private final int itemHeight = 18;

        private ModuleSelector(int x, int y, Map<String, ModuleEntry> entries) {
            this.x = x;
            this.y = y;
            this.options = new ArrayList<>();
            for (ModuleEntry entry : entries.values()) {
                if (entry != null && entry.name != null && Pipeline.isModuleAvailable(entry.name)) {
                    this.options.add(entry);
                }
            }
            this.width = 120;
        }

        private void render(DrawContext context, int mouseX, int mouseY) {
            int currentY = this.y;
            context.fill(this.x - 1, this.y - 1, this.x + this.width + 1,
                this.y + this.options.size() * this.itemHeight + 1, 0xFFFFFFFF);

            for (ModuleEntry entry : this.options) {
                boolean hovered = mouseX >= this.x && mouseX <= this.x + this.width
                    && mouseY >= currentY && mouseY <= currentY + this.itemHeight;
                context.fill(this.x, currentY, this.x + this.width, currentY + this.itemHeight,
                    hovered ? 0xFF444444 : 0xFF222222);
                context.drawTextWithShadow(textRenderer, Text.translatable(entry.name), this.x + 5,
                    currentY + 5, 0xFFE0E0E0);
                currentY += this.itemHeight;
            }
        }

        private boolean onClick(double mouseX, double mouseY) {
            if (mouseX < this.x || mouseX > this.x + this.width || mouseY < this.y
                || mouseY > this.y + this.options.size() * this.itemHeight) {
                return false;
            }

            int index = (int) ((mouseY - this.y) / this.itemHeight);
            if (index >= 0 && index < this.options.size()) {
                ModuleEntry selected = this.options.get(index);
                if (!Pipeline.isModuleAvailable(selected.name)) {
                    return true;
                }
                Module module = selected.loadModule();
                module.x = 100;
                module.y = 100;
                nodes.add(new ModuleNode(module));
                return true;
            }

            return false;
        }
    }

    private class PresetSelector {

        private final int x;
        private final int y;
        private final int width;
        private final List<PresetEntry> options;
        private final int itemHeight = 18;

        private PresetSelector(int x, int y, List<PresetEntry> presets) {
            this.x = x;
            this.y = y;
            this.options = new ArrayList<>(presets);
            this.width = 140;
        }

        private void render(DrawContext context, int mouseX, int mouseY) {
            int currentY = this.y;
            context.fill(this.x - 1, this.y - 1, this.x + this.width + 1,
                this.y + this.options.size() * this.itemHeight + 1, 0xFFFFFFFF);

            for (PresetEntry entry : this.options) {
                boolean hovered = mouseX >= this.x && mouseX <= this.x + this.width
                    && mouseY >= currentY && mouseY <= currentY + this.itemHeight;
                context.fill(this.x, currentY, this.x + this.width, currentY + this.itemHeight,
                    hovered ? 0xFF444444 : 0xFF222222);
                context.drawTextWithShadow(textRenderer, Text.translatable(entry.name()),
                    this.x + 5, currentY + 5, 0xFFE0E0E0);
                currentY += this.itemHeight;
            }
        }

        private boolean onClick(double mouseX, double mouseY) {
            if (mouseX < this.x || mouseX > this.x + this.width || mouseY < this.y
                || mouseY > this.y + this.options.size() * this.itemHeight) {
                return false;
            }

            int index = (int) ((mouseY - this.y) / this.itemHeight);
            if (index >= 0 && index < this.options.size()) {
                activePreset = this.options.get(index);
                presetScrollY = 0;
                applyActivePreset();
                return true;
            }

            return false;
        }
    }
}
