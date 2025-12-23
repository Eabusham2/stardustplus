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

/**
 * @author Tas [0xTas] <root@0xTas.dev>
 **/
@Mixin(World.class)
public abstract class WorldMixin implements WorldAccess, AutoCloseable {

    private static boolean shouldCancel(SoundEvent sound) {
        Modules modules = Modules.get();
        if (modules == null) return false;

        AutoSmith smith = modules.get(AutoSmith.class);
        StashBrander brander = modules.get(StashBrander.class);

        if (brander != null && brander.isActive() && brander.shouldMute()) {
            if (sound == SoundEvents.BLOCK_ANVIL_USE || sound == SoundEvents.BLOCK_ANVIL_BREAK) return true;
        }

        if (smith != null && smith.isActive() && smith.muteSmithy.get()) {
            if (sound == SoundEvents.BLOCK_SMITHING_TABLE_USE) return true;
        }

        return false;
    }

    // OLD name (some versions) — make optional so it won't crash if missing
    @Inject(
        method = "playSoundAtBlockCenter",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void stardust$playSoundAtBlockCenter(BlockPos pos, SoundEvent sound, SoundCategory category,
                                                float volume, float pitch, boolean useDistance, CallbackInfo ci) {
        if (shouldCancel(sound)) ci.cancel();
    }

    // NEWER name (some versions) — also optional
    @Inject(
        method = "playSound",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void stardust$playSound(BlockPos pos, SoundEvent sound, SoundCategory category,
                                    float volume, float pitch, boolean useDistance, CallbackInfo ci) {
        if (shouldCancel(sound)) ci.cancel();
    }
}
