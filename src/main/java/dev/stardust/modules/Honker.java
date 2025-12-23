package dev.stardust.modules;

import dev.stardust.Stardust;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ProvidedStringSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.GoatHornItem;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;

import java.util.Collection;

/**
 * @author Tas [0xTas] <root@0xTas.dev>
 **/
public class Honker extends Module {
    public Honker() {
        super(Stardust.CATEGORY, "Honker", "Automatically use goat horns when a player enters your render distance.");
    }

    public static String[] horns = {"Admire", "Call", "Dream", "Feel", "Ponder", "Seek", "Sing", "Yearn", "Random"};

    private final Setting<String> desiredCall = settings.getDefaultGroup().add(
        new ProvidedStringSetting.Builder()
            .name("horn-preference")
            .description("Which horn to prefer using")
            .supplier(() -> horns)
            .defaultValue("Random")
            .build()
    );

    private final Setting<Boolean> ignoreFakes = settings.getDefaultGroup().add(
        new BoolSetting.Builder()
            .name("ignore-fakes")
            .description("Ignore fake players created by modules like Blink.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> hornSpam = settings.getDefaultGroup().add(
        new BoolSetting.Builder()
            .name("horn-spam")
            .description("Spam the desired horn as soon as it's done cooling down (every 7 seconds.)")
            .defaultValue(false)
            .onChanged(it -> { if (it) this.ticksSinceUsedHorn = 420; })
            .build()
    );

    private final Setting<Boolean> hornSpamAlone = settings.getDefaultGroup().add(
        new BoolSetting.Builder()
            .name("when-alone")
            .description("If you really want to, I guess..")
            .defaultValue(false)
            .visible(hornSpam::get)
            .onChanged(it -> { if (it) this.ticksSinceUsedHorn = 420; })
            .build()
    );

    private final Setting<Boolean> muteHorns = settings.getDefaultGroup().add(
        new BoolSetting.Builder()
            .name("mute-horns")
            .description("Clientside mute for your own goat horns.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> muteAllHorns = settings.getDefaultGroup().add(
        new BoolSetting.Builder()
            .name("mute-all-horns")
            .description("Mute everybody's horns, not just your own.")
            .visible(muteHorns::get)
            .defaultValue(false)
            .build()
    );

    private int ticksSinceUsedHorn = 0;
    private boolean needsMuting = false;

    private void honkHorn(int hornSlot, int activeSlot) {
        if (mc.player == null || mc.interactionManager == null) return;

        needsMuting = true;
        InvUtils.move().from(hornSlot).to(activeSlot);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        InvUtils.move().from(activeSlot).to(hornSlot);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    /**
     * We avoid relying on registry APIs / LazyRegistryEntryReference methods that changed.
     * Instead, we stringify the INSTRUMENT component and look for the desired horn token.
     */
    private static boolean stackMatchesHornName(ItemStack stack, String desired) {
        if (!stack.contains(DataComponentTypes.INSTRUMENT)) return false;

        Object instrumentComp = stack.get(DataComponentTypes.INSTRUMENT);
        if (instrumentComp == null) return false;

        String text = normalize(instrumentComp.toString());
        // desired like "admire_goat_horn"
        return text.contains(desired);
    }

    private void honkDesiredHorn() {
        if (mc.player == null) return;

        int active = mc.player.getInventory().getSelectedSlot();

        // Random horn mode
        if ("Random".equals(desiredCall.get())) {
            IntArrayList hornSlots = new IntArrayList();
            for (int i = 0; i < mc.player.getInventory().size(); i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() instanceof GoatHornItem) hornSlots.add(i);
            }
            if (hornSlots.isEmpty()) return;

            int pick = hornSlots.size() == 1
                ? hornSlots.getInt(0)
                : hornSlots.getInt((int) (Math.random() * hornSlots.size()));

            honkHorn(pick, active);
            return;
        }

        // Specific horn mode
        String desiredToken = normalize(desiredCall.get()) + "_goat_horn";

        int hornIndex = -1;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!(stack.getItem() instanceof GoatHornItem)) continue;

            // If the stack doesn't carry INSTRUMENT we can't match; skip.
            if (!stack.contains(DataComponentTypes.INSTRUMENT)) continue;

            // If we match the desired horn token, pick it.
            if (stackMatchesHornName(stack, desiredToken)) {
                hornIndex = i;
                break;
            }

            // Fallback: if we haven't found anything yet, remember the first horn.
            if (hornIndex == -1) hornIndex = i;
        }

        if (hornIndex != -1) honkHorn(hornIndex, active);
    }

    // See GoatHornItemMixin.java
    public boolean shouldMuteHorns() {
        return (muteHorns.get() && needsMuting) || (muteHorns.get() && muteAllHorns.get());
    }

    @Override
    public void onActivate() {
        if (mc.world == null || mc.player == null) return;

        if (hornSpam.get()) {
            ticksSinceUsedHorn = 0;
            return;
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity && !(entity instanceof ClientPlayerEntity)) {
                if (ignoreFakes.get()) {
                    Collection<PlayerListEntry> players = mc.player.networkHandler.getPlayerList();
                    if (players.stream().noneMatch(entry -> entry.getProfile().getId().equals(entity.getUuid()))) continue;
                }
                honkDesiredHorn();
                break;
            }
        }

        needsMuting = false;
        ticksSinceUsedHorn = 0;
    }

    @EventHandler
    private void onEntityAdd(EntityAddedEvent event) {
        if (hornSpam.get() || mc.player == null) return;
        if (!(event.entity instanceof PlayerEntity player)) return;
        if (player instanceof ClientPlayerEntity) return;

        if (ignoreFakes.get()) {
            Collection<PlayerListEntry> players = mc.player.networkHandler.getPlayerList();
            if (players.stream().noneMatch(entry -> entry.getProfile().getId().equals(player.getUuid()))) return;
        }

        honkDesiredHorn();
        needsMuting = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!hornSpam.get() || mc.player == null || mc.world == null) return;

        boolean playerNearby = false;
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity && !(entity instanceof ClientPlayerEntity)) {
                if (ignoreFakes.get()) {
                    Collection<PlayerListEntry> players = mc.player.networkHandler.getPlayerList();
                    if (players.stream().noneMatch(entry -> entry.getProfile().getId().equals(entity.getUuid()))) continue;
                }
                playerNearby = true;
                break;
            }
        }

        if (!playerNearby && !hornSpamAlone.get()) return;

        ItemStack activeItem = mc.player.getActiveItem();
        if (activeItem.contains(DataComponentTypes.FOOD) || (Utils.isThrowable(activeItem.getItem()) && mc.player.getItemUseTime() > 0)) return;

        ++ticksSinceUsedHorn;
        if (ticksSinceUsedHorn > 150) {
            honkDesiredHorn();
            needsMuting = false;
            ticksSinceUsedHorn = 0;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof PlaySoundFromEntityS2CPacket) || !shouldMuteHorns()) return;

        SoundEvent soundEvent = ((PlaySoundFromEntityS2CPacket) event.packet).getSound().value();

        // Mute if it matches any goat horn sound in the list.
        for (int i = 0; i < SoundEvents.GOAT_HORN_SOUND_COUNT; i++) {
            if (soundEvent == SoundEvents.GOAT_HORN_SOUNDS.get(i).value()) {
                event.cancel();
                break;
            }
        }
    }
}
