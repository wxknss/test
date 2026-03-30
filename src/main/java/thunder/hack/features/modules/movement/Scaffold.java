package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import thunder.hack.events.impl.PlayerUpdateEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.PlayerUtility;
import thunder.hack.utility.render.Render3DEngine;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AntiFireball extends Module {
    public AntiFireball() {
        super("AntiFireball", Category.COMBAT);
    }

    private final Setting<Float> range = new Setting<>("Range", 4.5f, 1f, 8f);
    private final Setting<Boolean> rotate = new Setting<>("Rotate", true);
    private final Setting<RayTrace> rayTrace = new Setting<>("RayTrace", RayTrace.OnlyTarget);
    private final Setting<Boolean> autoSword = new Setting<>("AutoSword", true);
    private final Setting<Boolean> render = new Setting<>("Render", true);
    private final Setting<Integer> renderTime = new Setting<>("RenderTime", 500, 100, 2000, v -> render.getValue());

    private final Timer timer = new Timer();
    private final Timer renderTimer = new Timer();
    private FireballEntity target;
    private float rotationYaw;
    private float rotationPitch;
    private Vec3d renderPos = null;
    private int prevSlot = -1;

    public enum RayTrace {
        OFF, OnlyTarget, AllEntities
    }

    @Override
    public void onEnable() {
        target = null;
        renderPos = null;
    }

    @Override
    public void onDisable() {
        if (prevSlot != -1 && autoSword.getValue()) {
            InventoryUtility.switchTo(prevSlot);
            prevSlot = -1;
        }
    }

    @EventHandler
    public void onUpdate(PlayerUpdateEvent e) {
        if (fullNullCheck()) return;

        updateTarget();

        if (target == null) {
            if (autoSword.getValue() && prevSlot != -1) {
                InventoryUtility.switchTo(prevSlot);
                prevSlot = -1;
            }
            return;
        }

        if (autoSword.getValue()) {
            int swordSlot = getSwordSlot();
            if (swordSlot != -1 && swordSlot != mc.player.getInventory().selectedSlot) {
                if (prevSlot == -1) prevSlot = mc.player.getInventory().selectedSlot;
                InventoryUtility.switchTo(swordSlot);
            }
        }

        if (rotate.getValue()) {
            calcRotations();
            mc.player.setYaw(rotationYaw);
            mc.player.setPitch(rotationPitch);
        }

        if (isInRange() && canHit()) {
            attack();
            renderPos = target.getPos();
            renderTimer.reset();
        }

        if (render.getValue() && renderPos != null && renderTimer.passedMs(renderTime.getValue())) {
            renderPos = null;
        }
    }

    @Override
    public void onRender3D(net.minecraft.client.util.math.MatrixStack stack) {
        if (!render.getValue() || renderPos == null) return;
        Render3DEngine.drawBlockBox(BlockPos.ofFloored(renderPos), Color.ORANGE, 2.0f);
    }

    private void updateTarget() {
        List<FireballEntity> fireballs = new CopyOnWriteArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof FireballEntity fireball && fireball.isAlive() && mc.player.distanceTo(fireball) <= range.getValue()) {
                fireballs.add(fireball);
            }
        }

        target = fireballs.stream()
                .min(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)))
                .orElse(null);
    }

    private boolean isInRange() {
        if (target == null) return false;
        return mc.player.distanceTo(target) <= range.getValue();
    }

    private boolean canHit() {
        if (target == null) return false;
        return PlayerUtility.squaredDistanceFromEyes(target.getPos()) <= range.getValue() * range.getValue();
    }

    private void calcRotations() {
        if (target == null) return;

        Vec3d targetVec = target.getPos();
        Vec3d eyes = mc.player.getEyePos();

        double diffX = targetVec.x - eyes.x;
        double diffY = targetVec.y - eyes.y;
        double diffZ = targetVec.z - eyes.z;

        rotationYaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90);
        rotationPitch = (float) (-Math.toDegrees(Math.atan2(diffY, Math.hypot(diffX, diffZ))));
    }

    private void attack() {
        if (target == null) return;

        if (rotate.getValue()) {
            sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(rotationYaw, rotationPitch, mc.player.isOnGround()));
        }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        timer.reset();
    }

    private int getSwordSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.DIAMOND_SWORD ||
                stack.getItem() == Items.NETHERITE_SWORD ||
                stack.getItem() == Items.IRON_SWORD ||
                stack.getItem() == Items.STONE_SWORD ||
                stack.getItem() == Items.WOODEN_SWORD) {
                return i;
            }
        }
        return -1;
    }
}
