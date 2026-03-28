package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import thunder.hack.core.Managers;
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
