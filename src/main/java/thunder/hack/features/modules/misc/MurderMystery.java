package thunder.hack.features.modules.misc;

import io.netty.buffer.Unpooled;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShearsItem;
import net.minecraft.item.SwordItem;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
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
    private final Setting<Boolean> detectiveTracker = new Setting<>("DetectiveTracker", false);
    private final Setting<Server> server = new Setting<>("Server", Server.FunnyGame);
    private final Setting<Boolean> publicChat = new Setting<>("PublicChat", false);
    private final Setting<Boolean> silentSwap = new Setting<>("SilentSwap", false);

    private enum Server { FunnyGame, Hypixel }

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
            if (found != null && !found.equals(mc.player.getName().getString()) && !found.equals(killerName)) {
                killerName = found;
                String msg = "\u26a0\ufe0f " + killerName + " \u0443\u0431\u0438\u0439\u0446\u0430 \u26a0\ufe0f";
                displayNotification(msg, 0xFF0000);
                if (publicChat.getValue()) sendPublicMessage(killerName + " \u0443\u0431\u0438\u0439\u0446\u0430");
            }
        }

        if (detectiveTracker.getValue()) {
            String found = findDetective();
            if (found != null && !found.equals(mc.player.getName().getString()) && !found.equals(detectiveName)) {
                detectiveName = found;
                String msg = "\u26a0\ufe0f " + detectiveName + " \u0434\u0435\u0442\u0435\u043a\u0442\u0438\u0432 \u26a0\ufe0f";
                displayNotification(msg, 0x00AAAA);
                if (publicChat.getValue()) sendPublicMessage(detectiveName + " \u0434\u0435\u0442\u0435\u043a\u0442\u0438\u0432");
            }
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!silentSwap.getValue()) return;

        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket packet) {
            PlayerEntity target = getEntityFromPacket(packet);
            if (target == null || target == mc.player || target.getName().getString().equals(killerName)) return;

            int weaponSlot = findWeaponSlot();
            if (weaponSlot == -1) return;

            if (isWeapon(mc.player.getInventory().getStack(mc.player.getInventory().selectedSlot))) return;

            int currentSlot = mc.player.getInventory().selectedSlot;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(weaponSlot));
            mc.player.getInventory().selectedSlot = weaponSlot;

            Managers.ASYNC.run(() -> {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
                mc.player.getInventory().selectedSlot = currentSlot;
            });
        }
    }

    private boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (server.getValue()) {
            case Hypixel -> stack.getItem() instanceof SwordItem;
            case FunnyGame -> stack.getItem() instanceof ShearsItem;
        };
    }

    private String findMurder() {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || AntiBot.bots.contains(player) || !player.isAlive()) continue;
            ItemStack held = player.getMainHandStack();
            if (held.isEmpty()) continue;
            if (server.getValue() == Server.Hypixel && held.getItem() instanceof SwordItem && held.getItem() == Items.IRON_SWORD)
                return player.getName().getString();
            if (server.getValue() == Server.FunnyGame && held.getItem() instanceof ShearsItem)
                return player.getName().getString();
        }
        return null;
    }

    private String findDetective() {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || AntiBot.bots.contains(player) || !player.isAlive()) continue;
            ItemStack held = player.getMainHandStack();
            if (held.isEmpty()) continue;
            if (held.getItem() == Items.BOW) {
                if (server.getValue() == Server.Hypixel) {
                    var enchants = held.get(DataComponentTypes.ENCHANTMENTS);
                    if (enchants != null && enchants.getEnchantments().stream().anyMatch(e -> e.matchesKey(Enchantments.POWER))) {
                        return player.getName().getString();
                    }
                } else {
                    return player.getName().getString();
                }
            }
        }
        return null;
    }

    private int findWeaponSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            boolean found = switch (server.getValue()) {
                case Hypixel -> stack.getItem() instanceof SwordItem;
                case FunnyGame -> stack.getItem() instanceof ShearsItem;
            };
            if (found) return i;
        }
        return -1;
    }

    private void displayNotification(String message, int color) {
        if (fullNullCheck()) return;

        String prefix = "\u230a" + Formatting.GOLD + "\u26a1" + Formatting.RESET + "\u230b";
        Style style = Style.EMPTY.withColor(TextColor.fromRgb(color));
        Text coloredMessage = Text.literal(prefix + " ").append(Text.literal(message).setStyle(style));
        mc.player.sendMessage(coloredMessage, false);
    }

    private void sendPublicMessage(String message) {
        if (fullNullCheck()) return;
        mc.player.networkHandler.sendChatMessage(message);
    }

    private PlayerEntity getEntityFromPacket(PlayerInteractEntityC2SPacket packet) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            packet.write(buf);
            int entityId = buf.readVarInt();
            Entity entity = mc.world.getEntityById(entityId);
            if (entity instanceof PlayerEntity pe) return pe;
        } catch (Exception ignored) {
        } finally {
            buf.release();
        }
        return null;
    }
}
