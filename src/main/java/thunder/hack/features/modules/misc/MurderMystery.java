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
import thunder.hack.core.Managers;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

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
    private String detectiveName = null;

    @Override
    public void onEnable() {
        killerName = null;
        detectiveName = null;
    }

    @Override
    public void onDisable() {
        killerName = null;
        detectiveName = null;
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (fullNullCheck()) return;

        if (event.getPacket() instanceof PlayerListS2CPacket) {
            killerName = null;
            detectiveName = null;
            return;
        }

        if (event.getPacket() instanceof EntityEquipmentUpdateS2CPacket packet) {
            int entityId = packet.getEntityId();
            Entity entity = mc.world.getEntityById(entityId);
            if (!(entity instanceof PlayerEntity player)) return;
            if (player == mc.player) return;
            if (!player.isAlive()) return;

            for (var pair : packet.getEquipmentList()) {
                if (pair.getFirst() != EquipmentSlot.MAINHAND) continue;
                Item heldItem = pair.getSecond().getItem();
                if (heldItem == Items.AIR) continue;

                if (killerTracker.getValue()) {
                    if (server.getValue() == Server.Sword && heldItem instanceof SwordItem) {
                        updateKiller(player.getName().getString());
                    } else if (server.getValue() == Server.FunnyGame && heldItem instanceof ShearsItem) {
                        updateKiller(player.getName().getString());
                    }
                }

                if (detectiveTracker.getValue()) {
                    if (heldItem == Items.BOW) {
                        String name = player.getName().getString();
                        if (!name.equals(killerName)) {
                            updateDetective(name);
                        }
                    }
                }
                break;
            }
        }
    }

    private void updateKiller(String name) {
        if (name != null && !name.equals(killerName)) {
            killerName = name;
            String word = language.getValue() == Language.RU ? "\u0443\u0431\u0438\u0439\u0446\u0430" : "is the murderer";
            String msg = "\u26A0 " + killerName + " " + word + " \u26A0";
            displayNotification(msg, 0xFF0000);
            if (publicChat.getValue()) sendPublicMessage(killerName + " " + word);
        }
    }

    private void updateDetective(String name) {
        if (name != null && !name.equals(detectiveName)) {
            detectiveName = name;
            String word = language.getValue() == Language.RU ? "\u0434\u0435\u0442\u0435\u043a\u0442\u0438\u0432" : "is the detective";
            String msg = "\u26A0 " + detectiveName + " " + word + " \u26A0";
            displayNotification(msg, 0x00AAAA);
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (fullNullCheck()) return;
        if (!silentSwap.getValue()) return;

        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket packet) {
            PlayerEntity target = getEntityFromPacket(packet);
            if (target == null || target == mc.player) return;
            if (mc.player.distanceTo(target) > swapRange.getValue()) return;

            int weaponSlot = findWeaponSlot();
            if (weaponSlot == -1) return;
            if (isWeapon(mc.player.getInventory().getStack(mc.player.getInventory().selectedSlot))) return;

            int currentSlot = mc.player.getInventory().selectedSlot;
            event.cancel();

            sendPacket(new UpdateSelectedSlotC2SPacket(weaponSlot));
            mc.player.getInventory().selectedSlot = weaponSlot;

            if (noSwing.getValue()) {
                sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }

            Managers.ASYNC.run(() -> {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                sendPacket(PlayerInteractEntityC2SPacket.attack(target, false));
                sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
                mc.player.getInventory().selectedSlot = currentSlot;
            });
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

    public String getKillerName() { return killerName; }
    public String getDetectiveName() { return detectiveName; }
}
