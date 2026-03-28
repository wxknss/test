package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import thunder.hack.events.impl.EventMove;
import thunder.hack.events.impl.PlayerUpdateEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.math.MathUtility;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TPAura extends Module {
    public TPAura() {
        super("TPAura", Category.COMBAT);
    }

    private final Setting<Float> range = new Setting<>("Range", 50f, 1f, 100f);
    private final Setting<Float> hitRange = new Setting<>("HitRange", 3.5f, 1f, 6f);
    private final Setting<Integer> cps = new Setting<>("CPS", 10, 1, 20);
    private final Setting<Boolean> rotate = new Setting<>("Rotate", true);
    private final Setting<Integer> rotateRandomness = new Setting<>("RotateRandomness", 5, 0, 20, v -> rotate.getValue());
    private final Setting<Integer> teleportDelay = new Setting<>("TeleportDelay", 1, 0, 5);

    private final Timer attackTimer = new Timer();
    private final Timer teleportTimer = new Timer();
    private Entity target;
    private Vec3d originalPos;
    private int teleportTicks = 0;
    private boolean teleporting = false;
    private float lastYaw;
    private float lastPitch;

    @Override
    public void onEnable() {
        target = null;
        teleporting = false;
        teleportTicks = 0;
        lastYaw = mc.player.getYaw();
        lastPitch = mc.player.getPitch();
    }

    @EventHandler
    public void onUpdate(PlayerUpdateEvent e) {
        if (fullNullCheck()) return;

        updateTarget();

        if (target == null) {
            if (teleporting) {
                returnToOriginal();
            }
            return;
        }

        if (!teleporting && teleportTimer.passedMs(teleportDelay.getValue() * 50L)) {
            startTeleport();
        }

        if (teleporting && teleportTicks > 0) {
            teleportTicks--;
            if (teleportTicks <= 0) {
                attack();
                returnToOriginal();
            }
        }

        if (attackTimer.passedMs(1000 / cps.getValue()) && !teleporting) {
            attack();
        }
    }

    @EventHandler
    public void onMove(EventMove e) {
        if (teleporting) {
            e.setX(0);
            e.setZ(0);
            e.cancel();
        }
    }

    private void updateTarget() {
        List<Entity> entities = new CopyOnWriteArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (isValidTarget(entity)) {
                entities.add(entity);
            }
        }

        target = entities.stream()
                .min(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)))
                .orElse(null);
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == mc.player) return false;
        if (!(entity instanceof LivingEntity)) return false;
        if (!entity.isAlive()) return false;
        if (entity instanceof PlayerEntity player && (player.isCreative() || Managers.FRIEND.isFriend(player))) return false;
        if (mc.player.distanceTo(entity) > range.getValue()) return false;
        return true;
    }

    private void startTeleport() {
        if (target == null) return;

        originalPos = mc.player.getPos();
        Vec3d teleportPos = getTeleportPosition(target);

        if (teleportPos == null) return;

        mc.player.setPosition(teleportPos.x, teleportPos.y, teleportPos.z);
        sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround(teleportPos.x, teleportPos.y, teleportPos.z, false));

        teleporting = true;
        teleportTicks = 2;
        teleportTimer.reset();
    }

    private Vec3d getTeleportPosition(Entity target) {
        double range = hitRange.getValue();
        double yawRad = Math.toRadians(mc.player.getYaw());
        double directionX = -Math.sin(yawRad);
        double directionZ = Math.cos(yawRad);

        for (double r = 1.5; r <= range; r += 0.5) {
            Vec3d pos = new Vec3d(
                target.getX() + directionX * r,
                target.getY(),
                target.getZ() + directionZ * r
            );

            if (isPositionSafe(pos)) {
                return pos;
            }
        }
        return null;
    }

    private boolean isPositionSafe(Vec3d pos) {
        return !mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(pos.subtract(mc.player.getPos()))).iterator().hasNext();
    }

    private void attack() {
        if (target == null) return;

        if (rotate.getValue()) {
            float[] rotations = getRotations(target);
            float randomYawOffset = MathUtility.random(-rotateRandomness.getValue(), rotateRandomness.getValue());
            float randomPitchOffset = MathUtility.random(-rotateRandomness.getValue() / 2, rotateRandomness.getValue() / 2);
            
            lastYaw = rotations[0] + randomYawOffset;
            lastPitch = rotations[1] + randomPitchOffset;
            
            mc.player.setYaw(lastYaw);
            mc.player.setPitch(lastPitch);
            
            sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(lastYaw, lastPitch, mc.player.isOnGround()));
        }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        attackTimer.reset();
    }

    private void returnToOriginal() {
        if (originalPos == null) return;

        mc.player.setPosition(originalPos.x, originalPos.y, originalPos.z);
        sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround(originalPos.x, originalPos.y, originalPos.z, false));
        
        if (rotate.getValue()) {
            mc.player.setYaw(lastYaw);
            mc.player.setPitch(lastPitch);
            sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(lastYaw, lastPitch, mc.player.isOnGround()));
        }

        teleporting = false;
        teleportTicks = 0;
    }

    private float[] getRotations(Entity target) {
        Vec3d vec = target.getBoundingBox().getCenter();
        Vec3d eyes = mc.player.getEyePos();
        double diffX = vec.x - eyes.x;
        double diffY = vec.y - eyes.y;
        double diffZ = vec.z - eyes.z;

        double yaw = Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
        double pitch = -Math.toDegrees(Math.atan2(diffY, Math.hypot(diffX, diffZ)));

        return new float[]{(float) yaw, (float) pitch};
    }
}
