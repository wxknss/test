package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import thunder.hack.events.impl.PlayerUpdateEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TPAura extends Module {
    public TPAura() {
        super("TPAura", Category.COMBAT);
    }

    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Immediate);
    private final Setting<Float> range = new Setting<>("Range", 50f, 5f, 150f);
    private final Setting<Float> attackRange = new Setting<>("AttackRange", 3.5f, 1f, 6f);
    private final Setting<Integer> cps = new Setting<>("CPS", 10, 1, 20);
    private final Setting<Boolean> rotate = new Setting<>("Rotate", true);
    private final Setting<Integer> stickTicks = new Setting<>("StickTicks", 5, 1, 20, v -> mode.is(Mode.AStar));
    private final Setting<Integer> tickDistance = new Setting<>("TickDistance", 3, 1, 10, v -> mode.is(Mode.AStar));
    private final Setting<Integer> maxCost = new Setting<>("MaxCost", 250, 50, 500, v -> mode.is(Mode.AStar));
    private final Setting<Integer> delay = new Setting<>("Delay", 1, 0, 5, v -> mode.is(Mode.Safe));

    public enum Mode {
        Immediate, Instant, AStar, Safe
    }

    private final Timer timer = new Timer();
    private final Timer attackTimer = new Timer();
    private Entity target;
    private Vec3d originalPos;
    private Vec3d teleportPos;
    private List<Vec3d> path = new ArrayList<>();
    private int pathIndex = 0;
    private boolean teleporting = false;
    private boolean returning = false;
    private int stickCounter = 0;

    @Override
    public void onEnable() {
        target = null;
        teleporting = false;
        returning = false;
        path.clear();
        pathIndex = 0;
        stickCounter = 0;
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

        switch (mode.getValue()) {
            case Immediate:
                doImmediate();
                break;
            case Instant:
                doInstant();
                break;
            case AStar:
                doAStar();
                break;
            case Safe:
                doSafe();
                break;
        }
    }

    private void updateTarget() {
        List<Entity> entities = new ArrayList<>();
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
        if (entity instanceof PlayerEntity player) {
            if (player.isCreative()) return false;
        }
        if (mc.player.distanceTo(entity) > range.getValue()) return false;
        return true;
    }

    private void doImmediate() {
        if (!timer.passedMs(1000 / cps.getValue())) return;
        if (teleporting) return;

        originalPos = mc.player.getPos();
        teleportPos = getTeleportPosition(target);

        if (teleportPos == null) return;

        teleportTo(teleportPos);
        teleporting = true;
        timer.reset();
    }

    private void doInstant() {
        if (!timer.passedMs(1000 / cps.getValue())) return;

        originalPos = mc.player.getPos();
        teleportPos = getTeleportPosition(target);

        if (teleportPos == null) return;

        teleportTo(teleportPos);
        attack();
        teleportTo(originalPos);
        timer.reset();
    }

    private void doAStar() {
        if (teleporting) {
            if (returning) {
                if (pathIndex < path.size()) {
                    teleportTo(path.get(pathIndex));
                    pathIndex++;
                } else {
                    teleporting = false;
                    returning = false;
                    path.clear();
                    pathIndex = 0;
                }
            } else {
                if (pathIndex < path.size()) {
                    teleportTo(path.get(pathIndex));
                    pathIndex++;
                } else {
                    attack();
                    stickCounter++;
                    if (stickCounter >= stickTicks.getValue()) {
                        returning = true;
                        pathIndex = 0;
                        List<Vec3d> reversed = new ArrayList<>(path);
                        java.util.Collections.reverse(reversed);
                        path = reversed;
                        stickCounter = 0;
                    }
                }
            }
            timer.reset();
            return;
        }

        originalPos = mc.player.getPos();
        teleportPos = getTeleportPosition(target);
        
        if (teleportPos == null) return;
        
        path = buildPath(originalPos, teleportPos);
        if (path.isEmpty()) return;
        
        teleporting = true;
        returning = false;
        pathIndex = 0;
    }

    private void doSafe() {
        if (!timer.passedMs(1000 / cps.getValue() + delay.getValue() * 50L)) return;
        if (teleporting) return;

        originalPos = mc.player.getPos();
        teleportPos = getTeleportPosition(target);

        if (teleportPos == null) return;

        teleportTo(teleportPos);
        teleporting = true;
        
        mc.execute(() -> {
            attack();
            teleportTo(originalPos);
            teleporting = false;
        });
        timer.reset();
    }

    private void returnToOriginal() {
        if (originalPos != null) {
            teleportTo(originalPos);
        }
        teleporting = false;
        returning = false;
        path.clear();
        pathIndex = 0;
    }

    private void teleportTo(Vec3d pos) {
        mc.player.setPosition(pos.x, pos.y, pos.z);
        sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(pos.x, pos.y, pos.z, false));
    }

    private Vec3d getTeleportPosition(Entity target) {
        double yawRad = Math.toRadians(mc.player.getYaw());
        double directionX = -Math.sin(yawRad);
        double directionZ = Math.cos(yawRad);

        for (double r = 1.5; r <= attackRange.getValue(); r += 0.5) {
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

    private List<Vec3d> buildPath(Vec3d from, Vec3d to) {
        List<Vec3d> points = new ArrayList<>();
        double distance = from.distanceTo(to);
        int steps = (int) Math.ceil(distance / tickDistance.getValue());
        
        if (steps > maxCost.getValue()) {
            steps = maxCost.getValue();
        }
        
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double x = from.x + (to.x - from.x) * t;
            double y = from.y + (to.y - from.y) * t;
            double z = from.z + (to.z - from.z) * t;
            points.add(new Vec3d(x, y, z));
        }
        
        return points;
    }

    private void attack() {
        if (target == null) return;

        if (rotate.getValue()) {
            float[] rotations = getRotations(target);
            sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(rotations[0], rotations[1], mc.player.isOnGround()));
        }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        attackTimer.reset();
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

    private void sendMsg(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal(msg), false);
        }
    }
}
