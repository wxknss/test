package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import thunder.hack.core.Managers;
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

    private final Setting<Float> range = new Setting<>("Range", 50f, 5f, 150f);
    private final Setting<Float> attackRange = new Setting<>("AttackRange", 3.5f, 1f, 6f);
    private final Setting<Integer> cps = new Setting<>("CPS", 10, 1, 20);
    private final Setting<Boolean> rotate = new Setting<>("Rotate", true);
    private final Setting<Boolean> teleportBack = new Setting<>("TeleportBack", true);
    
    private final Setting<Boolean> players = new Setting<>("Players", true);
    private final Setting<Boolean> mobs = new Setting<>("Mobs", false);
    private final Setting<Boolean> animals = new Setting<>("Animals", false);
    private final Setting<Boolean> villagers = new Setting<>("Villagers", false);
    private final Setting<Boolean> hostiles = new Setting<>("Hostiles", true);
    private final Setting<Boolean> ignoreInvisible = new Setting<>("IgnoreInvisible", false);
    private final Setting<Boolean> ignoreNamed = new Setting<>("IgnoreNamed", false);
    private final Setting<Boolean> ignoreTeam = new Setting<>("IgnoreTeam", false);
    private final Setting<Boolean> ignoreCreative = new Setting<>("IgnoreCreative", true);
    private final Setting<Boolean> ignoreNaked = new Setting<>("IgnoreNaked", false);
    
    private final Setting<Boolean> vanillaDisabler = new Setting<>("VanillaDisabler", false);
    private final Setting<Float> distancePerPacket = new Setting<>("DistancePerPacket", 10f, 1f, 20f, v -> vanillaDisabler.getValue());
    
    private final Setting<Boolean> adaptToSpeed = new Setting<>("AdaptToSpeed", true);
    private final Setting<Float> speedMultiplier = new Setting<>("SpeedMultiplier", 3.0f, 1.0f, 10.0f, v -> adaptToSpeed.getValue());
    private final Setting<Boolean> adaptToFlight = new Setting<>("AdaptToFlight", true);
    private final Setting<Float> flightMultiplier = new Setting<>("FlightMultiplier", 5.0f, 1.0f, 15.0f, v -> adaptToFlight.getValue());
    
    private final Setting<Boolean> attackCooldown = new Setting<>("AttackCooldown", true);
    private final Setting<Integer> attackTickLimit = new Setting<>("AttackTickLimit", 11, 0, 20, v -> attackCooldown.getValue());

    private final Timer timer = new Timer();
    private Entity target;
    private Vec3d originalPos;
    private int hitTicks = 0;

    @Override
    public void onEnable() {
        target = null;
        hitTicks = 0;
    }

    @Override
    public void onDisable() {
        target = null;
    }

    @EventHandler
    public void onUpdate(PlayerUpdateEvent e) {
        if (fullNullCheck()) return;
        
        if (hitTicks > 0) {
            hitTicks--;
            return;
        }

        updateTarget();

        if (target == null) return;

        if (!timer.passedMs(1000 / cps.getValue())) return;

        originalPos = mc.player.getPos();
        Vec3d teleportPos = getTeleportPosition(target, getDynamicAttackRange());

        if (teleportPos == null) return;

        if (vanillaDisabler.getValue()) {
            sendVanillaPackets();
        }
        
        teleportTo(teleportPos);
        attack();
        
        if (teleportBack.getValue()) {
            teleportTo(originalPos);
        }
        
        hitTicks = attackTickLimit.getValue();
        timer.reset();
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
        if (entity instanceof ArmorStandEntity) return false;
        
        if (entity instanceof PlayerEntity player) {
            if (Managers.FRIEND.isFriend(player)) return false;
            if (!players.getValue()) return false;
            if (player.isCreative() && ignoreCreative.getValue()) return false;
            if (player.isInvisible() && ignoreInvisible.getValue()) return false;
            if (player.getArmor() == 0 && ignoreNaked.getValue()) return false;
            if (player.getTeamColorValue() == mc.player.getTeamColorValue() && ignoreTeam.getValue() && mc.player.getTeamColorValue() != 16777215) return false;
        } else if (entity instanceof VillagerEntity && !villagers.getValue()) {
            return false;
        } else if (entity instanceof MobEntity) {
            if (!mobs.getValue()) return false;
            if (entity instanceof AnimalEntity && !animals.getValue()) return false;
            if (entity instanceof HostileEntity && !hostiles.getValue()) return false;
        }
        
        if (entity.hasCustomName() && ignoreNamed.getValue()) return false;
        
        float currentRange = getDynamicRange();
        if (mc.player.distanceTo(entity) > currentRange) return false;
        
        return mc.player.canSee(entity);
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

    private void sendVanillaPackets() {
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

    private void attack() {
        if (target == null) return;

        if (rotate.getValue()) {
            float[] rotations = getRotations(target);
            sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(rotations[0], rotations[1], mc.player.isOnGround()));
        }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
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
    
    @Override
    public String getDisplayInfo() {
        if (target instanceof PlayerEntity player) {
            return player.getName().getString();
        }
        return null;
    }
}
