package thunder.hack.features.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import thunder.hack.events.impl.PlayerUpdateEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.PlayerUtility;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AntiFireball extends Module {
    public AntiFireball() {
        super("AntiFireball", Category.PLAYER);
    }

    private final Setting<Integer> cps = new Setting<>("CPS", 20, 1, 40);
    private final Setting<Float> range = new Setting<>("Range", 4.5f, 1f, 8f);
    private final Setting<Boolean> ignoreWhenBlocking = new Setting<>("IgnoreWhenBlocking", true);

    private final Timer timer = new Timer();
    private FireballEntity target;
    private int attacksThisTick = 0;

    @Override
    public void onEnable() {
        target = null;
        attacksThisTick = 0;
    }

    @EventHandler
    public void onUpdate(PlayerUpdateEvent e) {
        if (fullNullCheck()) return;

        if (ignoreWhenBlocking.getValue() && isBlocking()) {
            return;
        }

        updateTarget();

        if (target == null) return;

        if (!isInRange() || !canHit()) return;

        int maxAttacks = (cps.getValue() + 19) / 20;
        
        if (attacksThisTick < maxAttacks && timer.passedMs(1000 / Math.min(cps.getValue(), 20))) {
            attack();
            attacksThisTick++;
            timer.reset();
        }
        
        attacksThisTick = 0;
    }

    private boolean isBlocking() {
        if (mc.player == null) return false;
        
        // Проверяем, зажат ли правый клик (использование предмета)
        if (!mc.options.useKey.isPressed()) return false;
        
        // Проверяем, есть ли щит в любой руке
        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();
        
        return (mainHand.getItem() == Items.SHIELD || offHand.getItem() == Items.SHIELD);
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

    private void attack() {
        if (target == null) return;

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}
