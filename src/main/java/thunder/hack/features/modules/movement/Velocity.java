package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.injection.accesors.IClientPlayerEntity;
import thunder.hack.injection.accesors.IExplosionS2CPacket;
import thunder.hack.injection.accesors.ISPacketEntityVelocity;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.MovementUtility;

public class Velocity extends Module {
    public Velocity() {
        super("Velocity", Category.MOVEMENT);
    }

    // ===== НАСТРОЙКИ =====
    public Setting<Boolean> onlyAura = new Setting<>("OnlyDuringAura", false);
    public Setting<Boolean> pauseInWater = new Setting<>("PauseInLiquids", false);
    public Setting<Boolean> explosions = new Setting<>("Explosions", true);
    public Setting<Boolean> cc = new Setting<>("PauseOnFlag", false);
    public Setting<Boolean> fire = new Setting<>("PauseOnFire", false);
    
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.GrimNew);
    
    // ===== НАСТРОЙКИ ДЛЯ JUMP RESET (GrimNew) =====
    public Setting<Boolean> jumpReset = new Setting<>("JumpReset", true, v -> mode.is(Mode.GrimNew));
    public Setting<Float> jumpMotion = new Setting<>("JumpMotion", 0.42f, 0.4f, 0.5f, v -> mode.is(Mode.GrimNew) && jumpReset.getValue());
    public Setting<Integer> jumpDelay = new Setting<>("JumpDelay", 100, 50, 300, v -> mode.is(Mode.GrimNew) && jumpReset.getValue());
    public Setting<Boolean> reverse = new Setting<>("Reverse", true, v -> mode.is(Mode.GrimNew));
    
    // ===== ПЕРЕМЕННЫЕ =====
    private boolean flag;
    private int ccCooldown;
    private final Timer jumpTimer = new Timer();
    private boolean shouldJump = false;
    private double lastVelocityY = 0;

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (fullNullCheck()) return;

        // Проверки на паузу
        if (mc.player != null && (mc.player.isTouchingWater() || mc.player.isSubmergedInWater() || mc.player.isInLava()) && pauseInWater.getValue())
            return;

        if (mc.player != null && mc.player.isOnFire() && fire.getValue() && (mc.player.hurtTime > 0)) {
            return;
        }

        if (ccCooldown > 0) {
            ccCooldown--;
            return;
        }

        // ===== ОБРАБОТКА ВЕЛОСИТИ =====
        if (e.getPacket() instanceof EntityVelocityUpdateS2CPacket pac) {
            if (pac.getId() == mc.player.getId() && (!onlyAura.getValue() || ModuleManager.aura.isEnabled())) {
                
                switch (mode.getValue()) {
                    case Cancel -> {
                        // Полное обнуление
                        e.cancel();
                    }
                    case GrimNew -> {
                        // Реверсивный режим для Grim
                        e.cancel();
                        flag = true;
                        
                        // Сохраняем вертикальную скорость для прыжка
                        if (jumpReset.getValue()) {
                            lastVelocityY = pac.getVelocityY() / 8000.0; // конвертируем в игровую скорость
                        }
                    }
                }
            }
        }

        // ===== ОБРАБОТКА ВЗРЫВОВ =====
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

        // ===== ОБРАБОТКА LAGBACK (флаги) =====
        if (e.getPacket() instanceof PlayerPositionLookS2CPacket) {
            if (cc.getValue() || mode.is(Mode.GrimNew))
                ccCooldown = 5;
        }
    }

    @Override
    public void onUpdate() {
        if (fullNullCheck()) return;
        
        // Проверки на паузу
        if ((mc.player.isTouchingWater() || mc.player.isSubmergedInWater() || mc.player.isInLava()) && pauseInWater.getValue())
            return;

        if (mc.player.isOnFire() && fire.getValue() && (mc.player.hurtTime > 0))
            return;

        // ===== ОСНОВНАЯ ЛОГИКА =====
        switch (mode.getValue()) {
            case Cancel -> {
                // Ничего не делаем, просто отменяем пакеты
            }
            case GrimNew -> {
                // Реверсивная логика
                if (flag) {
                    if (ccCooldown <= 0) {
                        // Отправляем пакет с последними легитными ротациями
                        sendPacket(new PlayerMoveC2SPacket.Full(
                            mc.player.getX(), 
                            mc.player.getY(), 
                            mc.player.getZ(), 
                            ((IClientPlayerEntity) mc.player).getLastYaw(), 
                            ((IClientPlayerEntity) mc.player).getLastPitch(), 
                            mc.player.isOnGround()
                        ));
                        
                        // Отправляем STOP_DESTROY_BLOCK для синхронизации
                        sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, 
                            BlockPos.ofFloored(mc.player.getPos()), 
                            Direction.DOWN
                        ));
                    }
                    flag = false;
                }
                
                // ===== JUMP RESET (легитный джамп после получения велосити) =====
                if (jumpReset.getValue() && lastVelocityY > 0 && mc.player.isOnGround() && !mc.player.isTouchingWater()) {
                    if (jumpTimer.passedMs(jumpDelay.getValue())) {
                        shouldJump = true;
                        jumpTimer.reset();
                    }
                }
                
                // Выполняем прыжок
                if (shouldJump && mc.player.isOnGround()) {
                    if (reverse.getValue()) {
                        // Реверсивный прыжок (в противоположную сторону от удара)
                        double yaw = Math.toRadians(mc.player.getYaw());
                        double moveX = -Math.sin(yaw) * 0.2;
                        double moveZ = Math.cos(yaw) * 0.2;
                        mc.player.setVelocity(moveX, jumpMotion.getValue(), moveZ);
                    } else {
                        // Обычный прыжок
                        mc.player.setVelocity(mc.player.getVelocity().x, jumpMotion.getValue(), mc.player.getVelocity().z);
                    }
                    mc.player.jump();
                    shouldJump = false;
                    lastVelocityY = 0;
                }
            }
        }
    }

    @Override
    public void onEnable() {
        flag = false;
        ccCooldown = 0;
        shouldJump = false;
        lastVelocityY = 0;
        jumpTimer.reset();
    }

    public enum Mode {
        Cancel,      // Полное обнуление велосити
        GrimNew      // Реверсивный режим + Jump Reset для Grim
    }
}
