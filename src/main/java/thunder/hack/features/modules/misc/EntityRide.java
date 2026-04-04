package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.RideEntityC2SPacket;
import net.minecraft.util.Hand;
import thunder.hack.events.impl.PlayerUpdateEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

import java.util.Comparator;
import java.util.List;

public class EntityRide extends Module {
    public EntityRide() {
        super("EntityRide", Category.MISC);
    }

    private final Setting<Float> range = new Setting<>("Range", 5f, 1f, 10f);
    private final Setting<Boolean> players = new Setting<>("Players", true);
    private final Setting<Boolean> mobs = new Setting<>("Mobs", false);
    private final Setting<Boolean> autoRide = new Setting<>("AutoRide", false);
    private final Setting<Integer> rideDelay = new Setting<>("RideDelay", 20, 5, 100, v -> autoRide.getValue());

    private Entity target;
    private int rideCooldown = 0;

    @EventHandler
    public void onUpdate(PlayerUpdateEvent e) {
        if (fullNullCheck()) return;

        if (rideCooldown > 0) {
            rideCooldown--;
        }

        if (autoRide.getValue() && rideCooldown <= 0 && !mc.player.hasVehicle()) {
            findTarget();
            if (target != null) {
                ride(target);
                rideCooldown = rideDelay.getValue();
            }
        }
    }

    private void findTarget() {
        List<Entity> entities = mc.world.getEntities();
        target = entities.stream()
                .filter(this::isValidTarget)
                .min(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)))
                .orElse(null);
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == mc.player) return false;
        if (entity.hasVehicle()) return false;
        if (mc.player.distanceTo(entity) > range.getValue()) return false;
        
        if (entity instanceof PlayerEntity && !players.getValue()) return false;
        if (!(entity instanceof PlayerEntity) && !mobs.getValue()) return false;
        
        return true;
    }

    private void ride(Entity entity) {
        sendPacket(new RideEntityC2SPacket(entity, true));
        if (mc.player.getMainHandStack().isEmpty()) {
            mc.interactionManager.interactEntity(mc.player, entity, Hand.MAIN_HAND);
        }
        sendMessage("§aRiding: §7" + entity.getName().getString());
    }
}
