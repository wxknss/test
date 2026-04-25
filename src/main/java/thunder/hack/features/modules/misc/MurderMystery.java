package thunder.hack.features.modules.misc;

import io.netty.buffer.Unpooled;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShearsItem;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

import java.util.HashSet;
import java.util.Set;

public class MurderMystery extends Module {
    public MurderMystery() {
        super("MurderMystery", Category.MISC);
    }

    private final Setting<Boolean> killerTracker = new Setting<>("KillerTracker", true);
    private final Setting<Boolean> detectiveTracker = new Setting<>("DetectiveTracker", false);
    private final Setting<Server> server = new Setting<>("Server", Server.FunnyGame);
    private final Setting<Boolean> publicChat = new Setting<>("PublicChat", false);
    public final Setting<Boolean> NameColors = new Setting<>("NameColors", true);
    private final Setting<Message> message = new Setting<>("Message", Message.New);
    private final Setting<Boolean> silentSwap = new Setting<>("SilentSwap", false);
    private final Setting<Boolean> noSwing = new Setting<>("NoSwing", false, v -> silentSwap.getValue());

    private enum Server { FunnyGame, Sword }
    private enum Message { RU, EN, New, Hearts }

    private String killerName = null;
    private final Set<String> detectiveNames = new HashSet<>();
    private final Timer chatTimer = new Timer();
    private boolean sending = false;

    @Override
    public void onEnable() {
        killerName = null;
        detectiveNames.clear();
        chatTimer.reset();
    }

    @Override
    public void onDisable() {
        killerName = null;
        detectiveNames.clear();
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (fullNullCheck()) return;
        if (!isEnabled()) return;

        if (event.getPacket() instanceof PlayerListS2CPacket) {
            killerName = null;
            detectiveNames.clear();
            chatTimer.reset();
            return;
        }

        if (event.getPacket() instanceof EntityEquipmentUpdateS2CPacket packet) {
            for (var pair : packet.getEquipmentList()) {
                if (pair.getFirst() != EquipmentSlot.MAINHAND) continue;
                Item heldItem = pair.getSecond().getItem();
                if (heldItem == Items.AIR) continue;

                PlayerEntity target = null;
                for (PlayerEntity player : mc.world.getPlayers()) {
                    if (player == mc.player) continue;
                    if (player.getMainHandStack().getItem() == heldItem) {
                        target = player;
                        break;
                    }
                }
                if (target == null) continue;

                String name = target.getName().getString();

                if (killerTracker.getValue()) {
                    if (heldItem instanceof ShearsItem || heldItem == Items.IRON_SWORD) {
                        updateKiller(name);
                    }
                }

                if (detectiveTracker.getValue()) {
                    if (heldItem == Items.BOW && !name.equals(killerName)) {
                        updateDetective(name);
                    }
                }
            }
        }
    }

    private void updateKiller(String name) {
        if (name != null && !name.equals(killerName)) {
            killerName = name;
            String msg = "§#FF0000⚠ " + killerName + " §#FF0000убийца §#FF0000⚠";
            String publicMsg;

            switch (message.getValue()) {
                case RU -> publicMsg = killerName + " убийца";
                case EN -> publicMsg = killerName + " is the murderer";
                case Hearts -> publicMsg = "❤ " + killerName + " убийца ❤";
                default -> publicMsg = "⚠ " + killerName + " убийца ⚠";
            }

            displayNotification(msg);
            if (publicChat.getValue() && chatTimer.passedMs(5100)) {
                sendPublicMessage(publicMsg);
                chatTimer.reset();
            }
        }
    }

    private void updateDetective(String name) {
        if (name != null && detectiveNames.add(name)) {
            String msg = "§3⚠ " + name + " §3детектив §3⚠";
            displayNotification(msg);
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (fullNullCheck()) return;
        if (!isEnabled()) return;
        if (!silentSwap.getValue()) return;
        if (sending) return;

        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket packet) {
            PlayerEntity target = getEntityFromPacket(packet);
            if (target == null || target == mc.player) return;
            if (mc.player.distanceTo(target) > 3.0f) return;

            int weaponSlot = findWeaponSlot();
            if (weaponSlot == -1) return;
            if (isWeapon(mc.player.getInventory().getStack(mc.player.getInventory().selectedSlot))) return;

            sending = true;
            event.cancel();

            sendPacket(new UpdateSelectedSlotC2SPacket(weaponSlot));
            sendPacket(PlayerInteractEntityC2SPacket.attack(target, false));

            if (!noSwing.getValue()) {
                sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }

            sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
            sending = false;
        }
    }

    private boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (server.getValue()) {
            case Sword -> stack.getItem() == Items.IRON_SWORD;
            case FunnyGame -> stack.getItem() instanceof ShearsItem;
        };
    }

    private int findWeaponSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (isWeapon(stack)) return i;
        }
        return -1;
    }

    private void displayNotification(String message) {
        if (fullNullCheck()) return;
        String prefix = "\u230a" + Formatting.GOLD + "\u26a1" + Formatting.RESET + "\u230b";
        mc.player.sendMessage(Text.literal(prefix + " " + message), false);
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

    public String getKillerName() { return killerName; }
    public String getDetectiveName() { return detectiveNames.isEmpty() ? null : detectiveNames.iterator().next(); }
}
