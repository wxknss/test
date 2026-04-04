package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import thunder.hack.core.manager.client.ModuleManager;
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

    // ===== ОСНОВНЫЕ НАСТРОЙКИ =====
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Immediate);
    private final Setting<Float> range = new Setting<>("Range", 50f, 5f, 150f);
    private final Setting<Float> attackRange = new Setting<>("AttackRange", 3.5f, 1f, 6f);
    private final Setting<Integer> cps = new Setting<>("CPS", 10, 1, 20);
    private final Setting<Boolean> rotate = new Setting<>("Rotate", true);
    
    // ===== НАСТРОЙКИ ДЛЯ РАЗНЫХ РЕЖИМОВ =====
    private final Setting<Integer> stickTicks = new Setting<>("StickTicks", 5, 1, 20, v -> mode.is(Mode.AStar));
    private final Setting<Integer> tickDistance = new Setting<>("TickDistance", 3, 1, 10, v -> mode.is(Mode.AStar));
    private final Setting<Integer> maxCost = new Setting<>("MaxCost", 250, 50, 500, v -> mode.is(Mode.AStar));
    private final Setting<Integer> delay = new Setting<>("Delay", 1, 0, 5, v -> mode.is(Mode.Safe));
    
    // ===== 1.9 КУЛДАУН =====
    private final Setting<Boolean> use19Cooldown = new Setting<>("Use1_9Cooldown", true);
    private final Setting<Integer> cooldownTicks = new Setting<>("CooldownTicks", 20, 1, 40, v -> use19Cooldown.getValue());
    
    // ===== VANILLA DISABLER (из LiquidBounce) =====
    private final Setting<Boolean> vanillaDisabler = new Setting<>("VanillaDisabler", false);
    private final Setting<Float> distancePerPacket = new Setting<>("DistancePerPacket", 10f, 1f, 20f, v -> vanillaDisabler.getValue());
    
    // ===== АДАПТАЦИЯ ПОД FLIGHT/SPEED =====
    private final Setting<Boolean> adaptToSpeed = new Setting<>("AdaptToSpeed", true);
    private final Setting<Float> speedMultiplier = new Setting<>("SpeedMultiplier", 3.0f, 1.0f, 10.0f, v -> adaptToSpeed.getValue());
    private final Setting<Boolean> adaptToFlight = new Setting<>("AdaptToFlight", true);
    private final Setting<Float> flightMultiplier = new Setting<>("FlightMultiplier", 5.0f, 1.0f, 15.0f, v -> adaptToFlight.getValue());
    
    // ===== МЕТОДЫ ТЕЛЕПОРТА =====
    private final Setting<TeleportMethod> teleportMethod = new Setting<>("TeleportMethod", TeleportMethod.Standard);
    private final Setting<Boolean> teleportBack = new Setting<>("TeleportBack", true);
    private final Setting<Integer> teleportDelay = new Setting<>("TeleportDelay", 0, 0, 10, v -> teleportMethod.is(TeleportMethod.Delayed));

    public enum Mode {
        Immediate, Instant, AStar, Safe
    }
    
    public enum TeleportMethod {
        Standard,      // обычный телепорт
        PacketSpoof,   // спам пакетов перед телепортом
        Delayed,       // с задержкой между пакетами
        Blink          // с использованием Blink
    }

    private final Timer timer = new Timer();
    private final Timer attackTimer = new Timer();
    private final Timer cooldownTimer = new Timer();
    private Entity target;
    private Vec3d originalPos;
    private Vec3d teleportPos;
    private List<Vec3d> path = new ArrayList<>();
    private int pathIndex = 0;
    private boolean teleporting = false;
    private boolean returning = false;
    private int stickCounter = 0;
    private double lastDistance = 0;

    @Override
    public void onEnable() {
        target = null;
        teleporting = false;
        returning = false;
        path.clear();
        pathIndex = 0;
        stickCounter = 0;
        cooldownTimer.reset();
    }

    @EventHandler
    public void onUpdate(PlayerUpdateEvent e) {
        if (fullNullCheck()) return;
        
        // 1.9 кулдаун
        if (use19Cooldown.getValue() && !cooldownTimer.passedMs(cooldownTicks.getValue() * 50L)) {
            return;
        }

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
        if (entity instanceof PlayerEntity player && player.isCreative()) return false;
        
        float currentRange = getDynamicRange();
        if (mc.player.distanceTo(entity) > currentRange) return false;
        return true;
    }
    
    private float getDynamicRange() {
        float baseRange = range.getValue();
        if (adaptToSpeed.getValue() && ModuleManager.speed.isEnabled()) {
            baseRange *= speedMultiplier.getValue();
        }
        if (adaptToFlight.getValue() && ModuleManager.flight.isEnabled()) {
            baseRange *= flightMultiplier.getValue();
        }
        return baseRange;
    }
    
    private float getDynamicAttackRange() {
        float baseRange = attackRange.getValue();
        if (adaptToSpeed.getValue() && ModuleManager.speed.isEnabled()) {
            baseRange *= speedMultiplier.getValue();
        }
        if (adaptToFlight.getValue() && ModuleManager.flight.isEnabled()) {
            baseRange *= flightMultiplier.getValue();
        }
        return baseRange;
    }

    private void doImmediate() {
        if (!timer.passedMs(1000 / cps.getValue())) return;
        if (teleporting) return;

        originalPos = mc.player.getPos();
        teleportPos = getTeleportPosition(target, getDynamicAttackRange());

        if (teleportPos == null) return;

        // Vanilla Disabler (перед телепортом спамим пакеты)
        if (vanillaDisabler.getValue()) {
            sendVanillaPackets();
        }

        teleportTo(teleportPos);
        teleporting = true;
        
        if (use19Cooldown.getValue()) {
            cooldownTimer.reset();
        }
        timer.reset();
    }

    private void doInstant() {
        if (!timer.passedMs(1000 / cps.getValue())) return;

        originalPos = mc.player.getPos();
        teleportPos = getTeleportPosition(target, getDynamicAttackRange());

        if (teleportPos == null) return;

        // Vanilla Disabler
        if (vanillaDisabler.getValue()) {
            sendVanillaPackets();
        }

        // Выбор метода телепорта
        switch (teleportMethod.getValue()) {
            case PacketSpoof:
                sendSpoofPackets();
                break;
            case Delayed:
                sendDelayedPackets();
                break;
            case Blink:
                sendBlinkTeleport();
                break;
            default:
                teleportTo(teleportPos);
        }
        
        attack();
        
        if (teleportBack.getValue()) {
            teleportTo(originalPos);
        }
        
        if (use19Cooldown.getValue()) {
            cooldownTimer.reset();
        }
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
                    if (use19Cooldown.getValue()) {
                        cooldownTimer.reset();
                    }
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
                        if (use19Cooldown.getValue()) {
                            cooldownTimer.reset();
                        }
                    }
                }
            }
            timer.reset();
            return;
        }

        originalPos = mc.player.getPos();
        teleportPos = getTeleportPosition(target, getDynamicAttackRange());
        
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
        teleportPos = getTeleportPosition(target, getDynamicAttackRange());

        if (teleportPos == null) return;

        teleportTo(teleportPos);
        teleporting = true;
        
        mc.execute(() -> {
            attack();
            if (teleportBack.getValue()) {
                teleportTo(originalPos);
            }
            teleporting = false;
            if (use19Cooldown.getValue()) {
                cooldownTimer.reset();
            }
        });
        timer.reset();
    }

    private void sendVanillaPackets() {
        // Аналог VanillaSpeed Disabler из LiquidBounce
        double distance = Math.sqrt(
            Math.pow(mc.player.getX() - mc.player.prevX, 2) +
            Math.pow(mc.player.getY() - mc.player.prevY, 2) +
            Math.pow(mc.player.getZ() - mc.player.prevZ, 2)
        );
        
        int packets = (int) (distance / distancePerPacket.getValue());
        
        for (int i = 0; i < packets; i++) {
            sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }
    }
    
    private void sendSpoofPackets() {
        // Спам пакетами перед телепортом (обходит некоторые античиты)
        for (int i = 0; i < 5; i++) {
            sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(),
                mc.player.getY() + 0.001 * i,
                mc.player.getZ(),
                true
            ));
        }
        teleportTo(teleportPos);
    }
    
    private void sendDelayedPackets() {
        // Отправка пакетов с задержкой
        new Thread(() -> {
            try {
                Thread.sleep(teleportDelay.getValue() * 10L);
                teleportTo(teleportPos);
            } catch (InterruptedException ignored) {}
        }).start();
    }
    
    private void sendBlinkTeleport() {
        // Использование Blink модуля для телепорта
        if (ModuleManager.blink.isEnabled()) {
            teleportTo(teleportPos);
        } else {
            teleportTo(teleportPos);
        }
    }

    private void returnToOriginal() {
        if (originalPos != null && teleportBack.getValue()) {
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

    private Vec3d getTeleportPosition(Entity target, float range) {
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
}
