package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import thunder.hack.core.Managers;
import thunder.hack.events.impl.EventMove;
import thunder.hack.events.impl.EventSync;
import thunder.hack.events.impl.PlayerUpdateEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.PlayerUtility;

public class ElytraPlus extends Module {
    public ElytraPlus() {
        super("Elytra+", Category.MOVEMENT);
    }

    public enum Mode { FireWork, GrimBoost }

    private final Setting<Mode> mode = new Setting<>("Mode", Mode.GrimBoost);
    private final Setting<Float> factor = new Setting<>("Factor", 0.09f, 0.01f, 1.0f, v -> mode.is(Mode.GrimBoost));
    private final Setting<Boolean> boostWhenBlinking = new Setting<>("BoostWhenBlinking", false, v -> mode.is(Mode.GrimBoost));
    private final Setting<Integer> fireSlot = new Setting<>("FireSlot", 1, 1, 9, v -> mode.is(Mode.FireWork));
    private final Setting<Float> fireDelay = new Setting<>("FireDelay", 1.5f, 0f, 5f, v -> mode.is(Mode.FireWork));
    private final Setting<Boolean> allowFireSwap = new Setting<>("AllowFireSwap", false, v -> mode.is(Mode.FireWork));

    private final Timer stopWatch = new Timer();
    private final Timer swapWatch = new Timer();
    private int prevSlot = -1;
    private boolean hasElytraEquipped = false;

    @Override
    public void onEnable() {
        if (mode.is(Mode.FireWork)) {
            if (InventoryUtility.findItemInInventory(Items.ELYTRA).slot() == -1) {
                disable("Нет элитр в инвентаре!");
                return;
            }
            if (InventoryUtility.findItemInInventory(Items.FIREWORK_ROCKET).slot() == -1) {
                disable("Нет фейерверков в инвентаре!");
                return;
            }
        }
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (mode.is(Mode.FireWork)) {
            if (mc.player.isFallFlying()) {
                int fireworkSlot = getFireworkSlot();
                if (fireworkSlot != -1 && stopWatch.passedMs((long) (fireDelay.getValue() * 1000))) {
                    useFirework(fireworkSlot);
                    stopWatch.reset();
                }
            }
        }
    }

    @EventHandler
    public void onUpdate(PlayerUpdateEvent e) {
        if (mode.is(Mode.FireWork)) {
            if (!mc.player.isFallFlying() && mc.player.fallDistance > 0 && hasElytra()) {
                sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        }
    }

    @EventHandler
    public void onMove(EventMove e) {
        if (mode.is(Mode.GrimBoost)) {
            if (mc.player.isFallFlying() && !mc.player.isOnGround()) {
                double yaw = Math.toRadians(mc.player.getYaw() + 90.0f);
                Vec3d movement = new Vec3d(e.getX(), e.getY(), e.getZ());
                double dx = factor.getValue() * Math.cos(yaw);
                double dz = factor.getValue() * Math.sin(yaw);
                movement = movement.add(dx, 0.0, dz);
                e.setX(movement.x);
                e.setY(movement.y);
                e.setZ(movement.z);
            }
        }
    }

    private boolean hasElytra() {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return chest.getItem() == Items.ELYTRA;
    }

    private int getFireworkSlot() {
        if (mc.player.getOffHandStack().getItem() == Items.FIREWORK_ROCKET) {
            return -2;
        }

        int hotbarSlot = InventoryUtility.findItemInHotBar(Items.FIREWORK_ROCKET).slot();
        if (hotbarSlot != -1) return hotbarSlot;

        if (allowFireSwap.getValue()) {
            int invSlot = InventoryUtility.findItemInInventory(Items.FIREWORK_ROCKET).slot();
            if (invSlot != -1) {
                moveFireworksToHotbar(invSlot);
                return fireSlot.getValue() - 1;
            }
        }

        return -1;
    }

    private void moveFireworksToHotbar(int slot) {
        clickSlot(slot);
        clickSlot(fireSlot.getValue() - 1 + 36);
        clickSlot(slot);
    }

    private void useFirework(int slot) {
        boolean offhand = slot == -2;
        int currentSlot = mc.player.getInventory().selectedSlot;

        if (!offhand && currentSlot != slot) {
            sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }

        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(
                offhand ? Hand.OFF_HAND : Hand.MAIN_HAND,
                id,
                mc.player.getYaw(),
                mc.player.getPitch()
        ));

        if (!offhand && currentSlot != slot) {
            sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
        }
    }
}
