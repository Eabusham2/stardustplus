package dev.stardust.mixin;

import dev.stardust.modules.AntiToS;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.render.block.entity.AbstractSignBlockEntityRenderer;

import java.util.Arrays;
import java.util.stream.Collectors;

@Mixin(AbstractSignBlockEntityRenderer.class)
public abstract class AbstractSignBlockEntityRendererMixin {
    // Keep your AntiToS text replacement (works off SignText)
    @ModifyVariable(method = "renderText", at = @At("HEAD"), argsOnly = true)
    private SignText modifyRenderedText(SignText signText) {
        Modules modules = Modules.get();
        if (modules == null) return signText;

        AntiToS antiToS = modules.get(AntiToS.class);
        if (antiToS == null || !antiToS.isActive()) return signText;

        String testText = Arrays.stream(signText.getMessages(false))
            .map(Text::getString)
            .collect(Collectors.joining(" "))
            .trim();

        return antiToS.containsBlacklistedText(testText)
            ? antiToS.familyFriendlySignText(signText)
            : signText;
    }

    // NEW: cancel SIGN TEXT rendering here (instead of injecting into render(...))
    @Inject(method = "renderText", at = @At("HEAD"), cancellable = true)
    private void stardust$onRenderText(BlockPos pos, SignText signText, MatrixStack matrices,
                                       VertexConsumerProvider vertexConsumers, int light,
                                       int textLineHeight, int maxTextWidth, boolean front,
                                       CallbackInfo ci) {
        Modules mods = Modules.get();
        if (mods == null) return;

        NoRender noRender = mods.get(NoRender.class);
        AntiToS antiToS = mods.get(AntiToS.class);

        // Cody-sign hiding (by text match)
        if (noRender != null && noRender.isActive()) {
            var signSetting = noRender.settings.get("cody-signs");
            if (signSetting != null && (boolean) signSetting.get() && isCodySignText(signText)) {
                ci.cancel();
                return;
            }
        }

        // AntiToS NoRender mode (hide blacklisted sign text)
        if (antiToS != null && antiToS.isActive() && antiToS.signMode.get().equals(AntiToS.SignMode.NoRender)) {
            String joined = Arrays.stream(signText.getMessages(false))
                .map(Text::getString)
                .collect(Collectors.joining(" "));
            if (antiToS.containsBlacklistedText(joined)) {
                ci.cancel();
            }
        }
    }

    @Unique
    private boolean isCodySignText(SignText text) {
        return Arrays.stream(text.getMessages(false)).anyMatch(msg -> {
            String s = msg.getString();
            String lower = s.toLowerCase();
            return s.contains("codysmile11") || lower.contains("has been here :)");
        });
    }
}
