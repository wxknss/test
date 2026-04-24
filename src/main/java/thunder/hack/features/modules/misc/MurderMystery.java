package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShearsItem;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import thunder.hack.core.Managers;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.combat.AntiBot;
import thunder.hack.setting.Setting;

public class MurderMystery extends Module {
    public MurderMystery() {
        super("MurderMystery", Category.MISC);
    }

    private final Setting<Boolean> killerTracker = new Setting<>("KillerTracker", true);
    private final Setting<Boolean> detectiveTracker = new Setting<>("DetectiveTracker", true);
    private final Setting<Server> server = new Setting<>("Server", Server.Hypixel);
    private final Setting<Boolean> publicChat = new Setting<>("PublicChat", false);
    private final Setting<Boolean> silentSwap = new Setting<>("SilentSwap", false);

    private enum Server {
        Hypixel, FunnyGame
    }

    private String killerName = null;
    private String detectiveName = null;

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof PlayerListS2CPacket) {
            killerName = null;
            detectiveName = null;
        }
    }

    @Override
    public void onUpdate() {
        if (fullNullCheck()) return;

        if (killerTracker.getValue()) {
            String found = findMurder();
            if (found != null && !found.equals(mc.player.getName().getString())) {
                if (!found.equals(killerName)) {
                    killerName = found;
                    String msg = "⚠️ " + killerName + " убийца ⚠️";
                    displayNotification(msg, 0xFF0000);
                    if (publicChat.getValue()) sendPublicMessage(killerName + " убийца");
                }
            }
        }

        if (detectiveTracker.getValue()) {
            String found = findDetective();
            if (found != null && !found.equals(mc.player.getName().getString())) {
                if (!found.equals(detectiveName)) {
                    detectiveName = found;
                    String msg = "⚠️ " + detectiveName + " детектив ⚠️";
                    displayNotification(msg, 0x00AAAA);
                    if (publicChat.getValue()) sendPublicMessage(detectiveName + " детектив");
                }
            }
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!silentSwap.getValue()) return;

        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket packet) {
            PlayerEntity target = getEntityFromPacket(packet);
            if (target == null) return;
            if (target == mc.player) return;
            if (target.getName().getString().equals(killerName)) return;

            int weaponSlot = findWeaponSlot();
            if (weaponSlot == -1) return;

            if (isWeapon(mc.player.getInventory().getStack(mc.player.getInventory().selectedSlot))) return;

            int currentSlot = mc.player.getInventory().selectedSlot;

            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(weaponSlot));
            mc.player.getInventory().selectedSlot = weaponSlot;

            Managers.ASYNC.run(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                }
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
                mc.player.getInventory().selectedSlot = currentSlot;
            });
        }
    }

    private boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;

        switch (server.getValue()) {
            case Hypixel:
                return stack.getItem() instanceof SwordItem;
            case FunnyGame:
                return stack.getItem() instanceof ShearsItem;
        }
        return false;
    }

    private String findMurder() {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (AntiBot.bots.contains(player)) continue;
            if (!player.isAlive()) continue;

            ItemStack held = player.getMainHandStack();
            if (held.isEmpty()) continue;

            switch (server.getValue()) {
                case Hypixel:
                    if (held.getItem() instanceof SwordItem && held.getItem() == Items.IRON_SWORD) {
                        return player.getName().getString();
                    }
                    break;
                case FunnyGame:
                    if (held.getItem() instanceof ShearsItem) {
                        return player.getName().getString();
                    }
                    break;
            }
        }
        return null;
    }

    private String findDetective() {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (AntiBot.bots.contains(player)) continue;
            if (!player.isAlive()) continue;

            ItemStack held = player.getMainHandStack();
            if (held.isEmpty()) continue;

            switch (server.getValue()) {
                case Hypixel:
                    if (held.getItem() == Items.BOW) {
                        if (held.getEnchantments().toString().contains("power")) {
                            return player.getName().getString();
                        }
                    }
                    break;
                case FunnyGame:
                    if (held.getItem() == Items.BOW) {
                        return player.getName().getString();
                    }
                    break;
            }
        }
        return null;
    }

    private int findWeaponSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            switch (server.getValue()) {
                case Hypixel:
                    if (stack.getItem() instanceof SwordItem) return i;
                    break;
                case FunnyGame:
                    if (stack.getItem() instanceof ShearsItem) return i;
                    break;
            }
        }
        return -1;
    }

    private void displayNotification(String message, int color) {
        if (fullNullCheck()) return;

        String prefix = "⌊" + Formatting.GOLD + "⚡" + Formatting.RESET + "⌉";
        String hex = String.format("%06X", color);
        String coloredMessage = prefix + " §#" + hex + message;
        mc.player.sendMessage(Text.of(coloredMessage), false);
    }

    private void sendPublicMessage(String message) {
        if (fullNullCheck()) return;
        mc.player.networkHandler.sendChatMessage(message);
    }

    private PlayerEntity getEntityFromPacket(PlayerInteractEntityC2SPacket packet) {
        Entity entity = mc.world.getEntityById(((net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacketAccessor) packet).getEntityId());
        if (entity instanceof PlayerEntity) {
            return (PlayerEntity) entity;
        }
        return null;
    }
}
