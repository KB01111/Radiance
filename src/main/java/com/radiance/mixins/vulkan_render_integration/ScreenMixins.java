package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.RendererProxy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixins {

    private static final float DEFAULT_SCREEN_BLUR_RADIUS = 6.0f;

    @Shadow
    protected MinecraftClient client;

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"))
    private void applyInWorldBlur(DrawContext context, CallbackInfo ci) {
        if (this.client == null || this.client.world == null) {
            return;
        }
        if (this.client.currentScreen != (Screen) (Object) this) {
            return;
        }

        BufferProxy.updateOverlayPostUniform(DEFAULT_SCREEN_BLUR_RADIUS);
        RendererProxy.postBlur();
    }
}
