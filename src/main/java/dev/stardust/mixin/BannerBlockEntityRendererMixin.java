package dev.stardust.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.render.block.entity.BannerBlockEntityRenderer;

@Mixin(BannerBlockEntityRenderer.class)
public class BannerBlockEntityRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(BannerBlockEntity bannerBlockEntity, float tickDelta, MatrixStack matrices,
                          VertexConsumerProvider vertexConsumers, Vec3d pos, int light, int overlay,
                          CallbackInfo ci) {
        if (bannerBlockEntity.getWorld() == null) return;

        Modules mods = Modules.get();
        if (mods == null) return;

        NoRender noRender = mods.get(NoRender.class);
        if (noRender == null || !noRender.isActive()) return;

        Text bannerName = bannerBlockEntity.getCustomName();
        var bannerSetting = noRender.settings.get("cody-banners");
        if (bannerSetting == null || bannerName == null) return;

        if ((boolean) bannerSetting.get() && bannerName.getString().contains("codysmile11")) {
            ci.cancel();
        }
    }
}
