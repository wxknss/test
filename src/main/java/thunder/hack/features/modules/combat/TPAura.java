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
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import thunder.hack.ThunderHack;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.PlayerUpdateEvent;
import thunder.hack.injection.accesors.ILivingEntity;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TPAura extends Module {
    public TPAura() {
        super("TPAura", Category.COMBAT);
    }

    private final Setting<Float> range = new Setting<>("Range", 50f, 5f, 150f);
    private final Setting<Float> attackRange = new Setting<>("AttackRange", 3.5f, 1f, 6f);
    private final Setting<Float> attackCooldown = new Setting<>("AttackCooldown", 0.9f, 0.5f, 1f);
    private final Setting<Float> attackBaseTime = new Setting<>("AttackBaseTime", 0.5f, 0f, 2f);
    private final Setting<Boolean> rotate = new Setting<>("Rotate", true);
    private final Setting<Boolean> teleportBack = new Setting<>("TeleportBack", true);
    private final Setting<Boolean> vanillaDisabler = new Setting<>("VanillaDisabler", false);
    private final Setting<Integer> disablerPackets = new Setting<>("DisablerPackets", 3, 3, 5, v -> vanillaDisabler.getValue());
    private final Setting<Boolean> checkTeleportSpot = new Setting<>("CheckTeleportSpot", true);
    private final Setting<AttackHand> attackHand = new Setting<>("AttackHand", AttackHand.MainHand);
    
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
    
    private final Setting<Boolean> adaptToSpeed = new Setting<>("AdaptToSpeed", true);
    private final Setting<Float> speedMultiplier = new Setting<>("SpeedMultiplier", 3f, 1f, 10f, v -> adaptToSpeed.getValue());
    private final Setting<Boolean> adaptToFlight = new Setting<>("AdaptToFlight", true);
    private final Setting<Float> flightMultiplier = new Setting<>("FlightMultiplier", 5f, 1f, 15f, v -> adaptToFlight.getValue());

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
        if (!isEnabled()) return;
        
        if (hitTicks > 0) {
            hitTicks--;
            return;
        }

        if (getAttackCooldown() < attackCooldown.getValue()) return;

        updateTarget();

        if (target == null) return;

        originalPos = mc.player.getPos();
        Vec3d teleportPos = getTeleportPosition(target, getDynamicAttackRange());

        if (teleportPos == null) return;

        if (vanillaDisabler.getValue() && isEnabled()) {
            for (int i = 0; i < disablerPackets.getValue(); i++) {
                sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
            }
        }
        
        teleportTo(teleportPos);
        attack();
        
        if (teleportBack.getValue()) {
            teleportTo(originalPos);
        }
        
        hitTicks = 11;
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

    private void teleportTo(Vec3d pos) {
        if (!isEnabled()) return;
        
        mc.player.setPosition(pos.x, pos.y, pos.z);
        sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(pos.x, pos.y, pos.z, false));
    }

    private Vec3d getTeleportPosition(Entity target, float range) {
        double yawRad = Math.toRadians(mc.player.getYaw());
        double directionX = -Math.sin(yawRad);
        double directionZ = Math.cos(yawRad);

        Vec3d bestPos = null;
        double bestDistance = -1;
        
        for (double r = 1.5; r <= range; r += 0.5) {
            Vec3d pos = new Vec3d(
                target.getX() + directionX * r,
                target.getY(),
                target.getZ() + directionZ * r
            );
            
            if (checkTeleportSpot.getValue()) {
                if (isPositionSafe(pos)) {
                    double dist = Math.abs(mc.player.getX() - pos.x) + Math.abs(mc.player.getZ() - pos.z);
                    if (bestPos == null || dist < bestDistance) {
                        bestPos = pos;
                        bestDistance = dist;
                    }
                }
            } else {
                return pos;
            }
        }
        return bestPos;
    }

    private boolean isPositionSafe(Vec3d pos) {
        return !mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(pos.subtract(mc.player.getPos()))).iterator().hasNext();
    }

    private void attack() {
        if (target == null) return;
        if (!isEnabled()) return;

        if (rotate.getValue()) {
            float[] rotations = getRotations(target);
            sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(rotations[0], rotations[1], mc.player.isOnGround()));
        }

        mc.interactionManager.attackEntity(mc.player, target);
        
        switch (attackHand.getValue()) {
            case OffHand -> mc.player.swingHand(Hand.OFF_HAND);
            case MainHand -> mc.player.swingHand(Hand.MAIN_HAND);
            case None -> {}
        }
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

    public float getAttackCooldownProgressPerTick() {
        return (float) (1.0 / mc.player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED) * (20.0 * ThunderHack.TICK_TIMER));
    }

    public float getAttackCooldown() {
        return MathHelper.clamp(((float) ((ILivingEntity) mc.player).getLastAttackedTicks() + attackBaseTime.getValue()) / getAttackCooldownProgressPerTick(), 0.0F, 1.0F);
    }
    
    @Override
    public String getDisplayInfo() {
        if (target instanceof PlayerEntity player) {
            return player.getName().getString();
        }
        return null;
    }

    public enum AttackHand {
        MainHand,
        OffHand,
        None
    }
}
