package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import thunder.hack.ThunderHack;
import thunder.hack.events.impl.EventSync;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

public class Step extends Module {
    public Step() {
        super("Step", Category.MOVEMENT);
    }

    private final Setting<Float> height = new Setting<>("Height", 1.5f, 1.0f, 2.0f);
    private final Setting<Integer> delay = new Setting<>("Delay", 200, 0, 500);
    
    private final Timer timer = new Timer();
    private boolean stepping = false;
    
    @Override
    public void onUpdate() {
        if (fullNullCheck()) return;
        
        if (mc.player.isOnGround() && shouldStep() && timer.passedMs(delay.getValue())) {
            stepping = true;
            setStepHeight(height.getValue());
            timer.reset();
        } else if (!stepping) {
            setStepHeight(0.6f);
        }
    }
    
    @EventHandler
    public void onSync(EventSync e) {
        if (stepping && mc.player.isOnGround()) {
            stepping = false;
        }
    }
    
    private boolean shouldStep() {
        if (!isMoving()) return false;
        
        double yawRad = Math.toRadians(mc.player.getYaw());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        
        for (double y = 1.0; y <= height.getValue() + 0.3; y += 0.5) {
            var pos = net.minecraft.util.math.BlockPos.ofFloored(
                mc.player.getX() + forwardX * 0.6,
                mc.player.getY() + y,
                mc.player.getZ() + forwardZ * 0.6
            );
            if (!mc.world.getBlockState(pos).isAir()) return true;
        }
        return false;
    }
    
    private boolean isMoving() {
        return mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0;
    }
    
    private void setStepHeight(float h) {
        if (mc.player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT) != null) {
            mc.player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT).setBaseValue(h);
        }
    }
}
