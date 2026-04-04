package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.injection.accesors.IExplosionS2CPacket;
import thunder.hack.injection.accesors.ISPacketEntityVelocity;
import thunder.hack.setting.Setting;

public class Velocity extends Module {
    public Velocity() {
        super("Velocity", Category.MOVEMENT);
    }

    public Setting<Boolean> onlyAura = new Setting<>("OnlyDuringAura", false);
    public Setting<Boolean> pauseInWater = new Setting<>("PauseInLiquids", false);
    public Setting<Boolean> explosions = new Setting<>("Explosions", true);
    public Setting<Boolean> cc = new Setting<>("PauseOnFlag", false);
    public Setting<Boolean> fire = new Setting<>("PauseOnFire", false);
    private final Setting<modeEn> mode = new Setting<>("Mode", modeEn.GrimNew);
    public Setting<Float> vertical = new Setting<>("Vertical", 0.0f, 0.0f, 100.0f, v -> mode.getValue() == modeEn.Custom);
    public Setting<Float> horizontal = new Setting<>("Horizontal", 0.0f, 0.0f, 100.0f, v -> mode.getValue() == modeEn.Custom || mode.getValue() == modeEn.Jump);

    private boolean flag;
    private int ccCooldown;

    public enum modeEn {
        Matrix, Cancel, Sunrise, Custom, Redirect, OldGrim, Jump, GrimNew
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (fullNullCheck()) return;

        if (mc.player != null && (mc.player.isTouchingWater() || mc.player.isSubmergedInWater() || mc.player.isInLava()) && pauseInWater.getValue())
            return;

        if (mc.player != null && mc.player.isOnFire() && fire.getValue() && (mc.player.hurtTime > 0)) {
            return;
        }

        if (ccCooldown > 0) {
            ccCooldown--;
            return;
        }

        if (e.getPacket() instanceof EntityVelocityUpdateS2CPacket pac) {
            if (pac.getId() == mc.player.getId() && (!onlyAura.getValue() || ModuleManager.aura.isEnabled())) {
                switch (mode.getValue()) {
                    case Cancel -> e.cancel();
                    case GrimNew -> {
                        e.cancel();
                        flag = true;
                    }
                    case Custom -> {
                        ((ISPacketEntityVelocity) pac).setMotionX((int) ((float) pac.getVelocityX() * horizontal.getValue() / 100f));
                        ((ISPacketEntityVelocity) pac).setMotionY((int) ((float) pac.getVelocityY() * vertical.getValue() / 100f));
                        ((ISPacketEntityVelocity) pac).setMotionZ((int) ((float) pac.getVelocityZ() * horizontal.getValue() / 100f));
                    }
                    case Matrix -> {
                        if (!flag) {
                            e.cancel();
                            flag = true;
                        } else {
                            flag = false;
                            ((ISPacketEntityVelocity) pac).setMotionX(((int) (pac.getVelocityX() * -0.1)));
                            ((ISPacketEntityVelocity) pac).setMotionZ(((int) (pac.getVelocityZ() * -0.1)));
                        }
                    }
                }
            }
        }

        if (e.getPacket() instanceof ExplosionS2CPacket explosion && explosions.getValue()) {
            switch (mode.getValue()) {
                case Cancel -> {
                    ((IExplosionS2CPacket) explosion).setMotionX(0);
                    ((IExplosionS2CPacket) explosion).setMotionY(0);
                    ((IExplosionS2CPacket) explosion).setMotionZ(0);
                }
                case GrimNew -> {
                    ((IExplosionS2CPacket) explosion).setMotionX(0);
                    ((IExplosionS2CPacket) explosion).setMotionY(0);
                    ((IExplosionS2CPacket) explosion).setMotionZ(0);
                    flag = true;
                }
            }
        }

        if (e.getPacket() instanceof PlayerPositionLookS2CPacket) {
            if (cc.getValue() || mode.getValue() == modeEn.GrimNew)
                ccCooldown = 5;
        }
    }

    @Override
    public void onUpdate() {
        if (mode.getValue() == modeEn.GrimNew && flag) {
            if (ccCooldown <= 0) {
                sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()));
            }
            flag = false;
        }
    }
}
