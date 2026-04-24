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
import thunder.hack.setting.impl.BooleanSettingGroup;
import thunder.hack.setting.impl.SettingGroup;

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

    private final Setting<SettingGroup> intaveGroup = new Setting<>("Intave", new SettingGroup(false, 0));
    private final Setting<modeEn> intaveMode = new Setting<>("Mode", modeEn.GrimNew, v -> mode.getValue() == modeEn.Intave).addToGroup(intaveGroup);
    private final Setting<Integer> intaveCountTo = new Setting<>("IntaveCount", 4, 1, 6, v -> mode.getValue() == modeEn.Intave).addToGroup(intaveGroup);
    private final Setting<Integer> intaveCountPost = new Setting<>("IntavePost", 4, 2, 10, v -> mode.getValue() == modeEn.Intave).addToGroup(intaveGroup);
    private final Setting<Boolean> intavePacket = new Setting<>("IntavePacket", false, v -> mode.getValue() == modeEn.Intave).addToGroup(intaveGroup);
    private final Setting<Float> intaveSlow = new Setting<>("IntaveSlow", 0.03f, 0.001f, 0.1f, v -> mode.getValue() == modeEn.Intave).addToGroup(intaveGroup);

    private final Setting<SettingGroup> compensationGroup = new Setting<>("Compensation", new SettingGroup(false, 0));
    private final Setting<CompMode> compMode = new Setting<>("CompMode", CompMode.Default, v -> mode.getValue() == modeEn.Compensation).addToGroup(compensationGroup);
    private final Setting<Boolean> jumper = new Setting<>("JumpOnGround", false, v -> mode.getValue() == modeEn.Compensation && compMode.is(CompMode.Default)).addToGroup(compensationGroup);

    private boolean flag;
    private int ccCooldown;
    private int intaveTicks;
    private int intavePostTicks;
    private double lastMotionX, lastMotionY, lastMotionZ;
    private boolean gotVelo;

    public enum modeEn {
        Cancel, GrimNew, Custom, Matrix, Compensation, Intave, Jump
    }

    public enum CompMode {
        Default, DamageAngle
    }

    @Override
    public void onUpdate() {
        if (mode.getValue() == modeEn.GrimNew && flag) {
            if (ccCooldown <= 0) {
                sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()));
            }
            flag = false;
        }

        if (mode.getValue() == modeEn.Intave) {
            if (intavePostTicks > 0) {
                intavePostTicks--;
            }
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (fullNullCheck()) return;

        if (mc.player != null && (mc.player.isTouchingWater() || mc.player.isSubmergedInWater() || mc.player.isInLava()) && pauseInWater.getValue())
            return;

        if (mc.player != null && mc.player.isOnFire() && fire.getValue() && (mc.player.hurtTime > 0))
            return;

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

                    case Jump -> {
                        ((ISPacketEntityVelocity) pac).setMotionY((int) ((float) pac.getVelocityY() * vertical.getValue() / 100f));
                        mc.player.jump();
                    }

                    case Compensation -> {
                        lastMotionX = pac.getVelocityX() / 8000.0;
                        lastMotionY = pac.getVelocityY() / 8000.0;
                        lastMotionZ = pac.getVelocityZ() / 8000.0;
                        gotVelo = true;
                    }

                    case Intave -> {
                        intaveTicks++;
                        if (intaveTicks >= intaveCountTo.getValue()) {
                            intaveTicks = 0;
                            intavePostTicks = intaveCountPost.getValue();
                            double vx = mc.player.getVelocity().x * (1.0 - intaveSlow.getValue());
                            double vz = mc.player.getVelocity().z * (1.0 - intaveSlow.getValue());
                            mc.player.setVelocity(vx, mc.player.getVelocity().y, vz);
                            e.cancel();
                        } else if (intavePostTicks > 0) {
                            e.cancel();
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
                case Intave -> {
                    ((IExplosionS2CPacket) explosion).setMotionX(0);
                    ((IExplosionS2CPacket) explosion).setMotionY(0);
                    ((IExplosionS2CPacket) explosion).setMotionZ(0);
                }
            }
        }

        if (e.getPacket() instanceof PlayerPositionLookS2CPacket) {
            if (cc.getValue() || mode.getValue() == modeEn.GrimNew)
                ccCooldown = 5;
        }
    }

    @EventHandler
    public void onMove(thunder.hack.events.impl.EventMove e) {
        if (mode.getValue() != modeEn.Compensation) return;

        if (gotVelo) {
            if (compMode.is(CompMode.Default)) {
                if (jumper.getValue() && mc.player.isOnGround()) {
                    mc.player.jump();
                }
            } else if (compMode.is(CompMode.DamageAngle)) {
                double yawToMotion = Math.toDegrees(Math.atan2(lastMotionZ, lastMotionX));
                double direction = mc.player.getYaw() - yawToMotion;

                while (direction > 180.0) direction -= 360.0;
                while (direction < -180.0) direction += 360.0;

                if (direction > 120.0 || direction < -120.0) {
                    mc.player.input.movementForward = 1;
                    mc.player.input.movementSideways = 0;
                } else if (direction > -150.0 && direction < -60.0) {
                    mc.player.input.movementForward = 0;
                    mc.player.input.movementSideways = -1;
                } else if (direction > -60.0 && direction < 60.0) {
                    mc.player.input.movementForward = -1;
                    mc.player.input.movementSideways = 0;
                } else if (direction > 60.0 && direction < 150.0) {
                    mc.player.input.movementForward = 0;
                    mc.player.input.movementSideways = 1;
                }
            }

            gotVelo = false;
        }
    }
}
