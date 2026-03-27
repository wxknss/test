package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

public class Velocity extends Module {
    public Velocity() {
        super("Velocity", Category.MOVEMENT);
    }

    private final Setting<Mode> mode = new Setting<>("Mode", Mode.JumpReset);
    private final Setting<Float> jumpMotion = new Setting<>("JumpMotion", 0.42f, 0.3f, 0.6f, v -> mode.is(Mode.JumpReset));
    private final Setting<Integer> delay = new Setting<>("Delay", 50, 0, 200, v -> mode.is(Mode.JumpReset));
    
    private long lastHitTime = 0;
    
    public enum Mode {
        JumpReset,   // Легитный прыжок в момент удара
        Reverse      // Реверсивный
    }
    
    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (fullNullCheck()) return;
        
        if (e.getPacket() instanceof EntityVelocityUpdateS2CPacket pac) {
            if (pac.getId() == mc.player.getId()) {
                lastHitTime = System.currentTimeMillis();
                
                if (mode.is(Mode.JumpReset)) {
                    if (System.currentTimeMillis() - lastHitTime > delay.getValue()) return;
                    if (mc.player.isOnGround()) {
                        mc.player.jump();
                        mc.player.setVelocity(mc.player.getVelocity().x, jumpMotion.getValue(), mc.player.getVelocity().z);
                    }
                }
            }
        }
    }
    
    @Override
    public void onUpdate() {
        if (fullNullCheck()) return;
        
        if (mode.is(Mode.Reverse) && mc.player.hurtTime > 0 && mc.player.isOnGround()) {
            double yaw = Math.toRadians(mc.player.getYaw());
            double moveX = -Math.sin(yaw) * 0.2;
            double moveZ = Math.cos(yaw) * 0.2;
            mc.player.setVelocity(moveX, 0.42, moveZ);
            mc.player.jump();
        }
    }
}
