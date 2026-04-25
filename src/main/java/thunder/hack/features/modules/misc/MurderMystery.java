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
import net.minecraft.item.SwordItem;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
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
    private final Setting<Language> language = new Setting<>("Language", Language.RU);
    private final Setting<Boolean> silentSwap = new Setting<>("SilentSwap", false);
    private final Setting<Float> swapRange = new Setting<>("SwapRange", 3.0f, 3.0f, 6.0f, v -> silentSwap.getValue());
    private final Setting<Boolean> noSwing = new Setting<>("NoSwing", false, v -> silentSwap.getValue());

    private enum Server { FunnyGame, Sword }
    private enum Language { RU, EN }

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

                for (PlayerEntity player : mc.world.getPlayers()) {
                    if (player == mc.player) continue;
                    if (!player.isAlive()) continue;
                    if (player.getUuid().version() != 3) continue;
                    if (player.getName().getString().contains("-")) continue;
                    if (player.getMainHandStack().getItem() != heldItem) continue;

                    String name = player.getName().getString();

                    if (killerTracker.getValue()) {
                        if (heldItem instanceof ShearsItem || heldItem instanceof SwordItem) {
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
    }

    private void updateKiller(String name) {
        if (name != null && !name.equals(killerName)) {
            killerName = name;
            String word = language.getValue() == Language.RU ? "убийца" : "is the murderer";
            String msg = "§c⚠ §f" + killerName + " §c" + word + " §c⚠";
            displayNotification(msg, -1);
            if (publicChat.getValue() && chatTimer.passedMs(5100)) {
                sendPublicMessage("⚠ " + killerName + " " + word + " ⚠");
                chatTimer.reset();
            }
        }
    }

    private void updateDetective(String name) {
        if (name != null && detectiveNames.add(name)) {
            String word = language.getValue() == Language.RU ? "детектив" : "is the detective";
            String msg = "§3⚠ §f" + name + " §3" + word + " §3⚠";
            displayNotification(msg, -1);
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (fullNullCheck()) return;
        if (!silentSwap.getValue()) return;
        if (sending) return;

        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket packet) {
            PlayerEntity target = getEntityFromPacket(packet);
            if (target == null || target == mc.player) return;
            if (mc.player.distanceTo(target) > swapRange.getValue()) return;

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
            case Sword -> stack.getItem() instanceof SwordItem;
            case FunnyGame -> stack.getItem() instanceof ShearsItem;
        };
    }

    private int findWeaponSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            boolean found = switch (server.getValue()) {
                case Sword -> stack.getItem() instanceof SwordItem;
                case FunnyGame -> stack.getItem() instanceof ShearsItem;
            };
            if (found) return i;
        }
        return -1;
    }

    private void displayNotification(String message, int color) {
        if (fullNullCheck()) return;
        String prefix = "\u230a" + Formatting.GOLD + "\u26a1" + Formatting.RESET + "\u230b";
        Text coloredMessage;
        if (color == -1) {
            coloredMessage = Text.literal(prefix + " ").append(Text.literal(message));
        } else {
            Style style = Style.EMPTY.withColor(TextColor.fromRgb(color));
            coloredMessage = Text.literal(prefix + " ").append(Text.literal(message).setStyle(style));
        }
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

    public String getKillerName() { return killerName; }
    public String getDetectiveName() { return detectiveNames.isEmpty() ? null : detectiveNames.iterator().next(); }
}
