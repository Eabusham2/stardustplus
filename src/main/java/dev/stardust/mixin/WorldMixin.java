package dev.stardust.mixin;

import dev.stardust.modules.AutoSmith;
import dev.stardust.modules.StashBrander;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class WorldMixin implements WorldAccess, AutoCloseable {
    @Inject(method = "playSoundAtBlockCenterClient", at = @At("HEAD"), cancellable = true)
    private void mixinPlaySoundAtBlockCenterClient(BlockPos pos, SoundEvent sound, SoundCategory category,
                                                   float volume, float pitch, boolean useDistance,
                                                   CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules == null) return;

        AutoSmith smith = modules.get(AutoSmith.class);
        StashBrander brander = modules.get(StashBrander.class);

        if (brander != null && brander.isActive() && brander.shouldMute()) {
            if (sound == SoundEvents.BLOCK_ANVIL_USE || sound == SoundEvents.BLOCK_ANVIL_BREAK) ci.cancel();
        }
        if (smith != null && smith.isActive() && smith.muteSmithy.get()) {
            if (sound == SoundEvents.BLOCK_SMITHING_TABLE_USE) ci.cancel();
        }
    }
}
