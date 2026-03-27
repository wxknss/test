package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import thunder.hack.ThunderHack;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.EventSync;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.MovementUtility;

public class Step extends Module {
    // ===== НАСТРОЙКИ =====
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Grim);
    private final Setting<Float> height = new Setting<>("Height", 1.5f, 1.0f, 2.0f);
    private final Setting<Boolean> useTimer = new Setting<>("Timer", true, v -> mode.is(Mode.Grim));
    private final Setting<Boolean> pauseIfShift = new Setting<>("PauseIfShift", false);
    private final Setting<Integer> stepDelay = new Setting<>("StepDelay", 200, 0, 1000);
    private final Setting<Boolean> holeDisable = new Setting<>("HoleDisable", false);
    
    // ===== ПЕРЕМЕННЫЕ =====
    private final Timer stepTimer = new Timer();
    private boolean alreadyInHole;
    private boolean timer;
    private boolean isStepping = false;
    private int stepStage = 0;
    private double startY = 0;

    public Step() {
        super("Step", Category.MOVEMENT);
    }

    public enum Mode {
        Grim,      // Для GrimAC
        Matrix,    // Для Matrix
        Vanilla    // Ванильный (без байпасса)
    }

    @Override
    public void onEnable() {
        alreadyInHole = mc.player != null && MovementUtility.isHole(mc.player.getBlockPos());
        isStepping = false;
        stepStage = 0;
        stepTimer.reset();
    }

    @Override
    public void onDisable() {
        ThunderHack.TICK_TIMER = 1f;
        setStepHeight(0.6F);
        isStepping = false;
        stepStage = 0;
    }

    @Override
    public void onUpdate() {
        if (fullNullCheck()) return;
        
        // Проверка на дыру
        if (holeDisable.getValue() && MovementUtility.isHole(mc.player.getBlockPos()) && !alreadyInHole) {
            disable("Player in hole... Disabling...");
            return;
        }
        alreadyInHole = mc.player != null && MovementUtility.isHole(mc.player.getBlockPos());

        // Проверка на паузу
        if (pauseIfShift.getValue() && mc.options.sneakKey.isPressed()) {
            setStepHeight(0.6F);
            return;
        }

        // Проверка на полет/воду
        if (mc.player.getAbilities().flying || ModuleManager.freeCam.isOn() || mc.player.isRiding() || mc.player.isTouchingWater()) {
            setStepHeight(0.6F);
            return;
        }

        // Сброс таймера
        if (timer && mc.player.isOnGround()) {
            ThunderHack.TICK_TIMER = 1f;
            timer = false;
        }

        // Основная логика степа
        if (mc.player.isOnGround() && stepTimer.passedMs(stepDelay.getValue()) && shouldStep()) {
            switch (mode.getValue()) {
                case Grim -> doGrimStep();
                case Matrix -> doMatrixStep();
                case Vanilla -> setStepHeight(height.getValue());
            }
        } else if (!isStepping) {
            setStepHeight(0.6F);
        }
    }

    @EventHandler
    public void onStep(EventSync event) {
        if (mode.is(Mode.Grim) && isStepping) {
            double stepHeight = height.getValue();
            double[] offsets = getGrimOffsets(stepHeight);
            
            if (offsets != null && offsets.length > 0 && stepStage < offsets.length) {
                if (useTimer.getValue() && !timer) {
                    ThunderHack.TICK_TIMER = 1F / offsets.length;
                    timer = true;
                }
                
                // Отправляем пакет с нужной высотой
                double targetY = startY + offsets[stepStage];
                sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), 
                    targetY, 
                    mc.player.getZ(), 
                    false
                ));
                
                stepStage++;
                
                if (stepStage >= offsets.length) {
                    isStepping = false;
                    stepStage = 0;
                    stepTimer.reset();
                    ThunderHack.TICK_TIMER = 1f;
                    timer = false;
                }
            }
        }
    }
    
    private boolean shouldStep() {
        if (!MovementUtility.isMoving()) return false;
        
        // Проверяем, есть ли блок перед игроком на высоте
        double yawRad = Math.toRadians(mc.player.getYaw());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        
        double stepHeight = height.getValue();
        
        for (double y = 1.0; y <= stepHeight + 0.5; y += 0.5) {
            net.minecraft.util.math.BlockPos checkPos = net.minecraft.util.math.BlockPos.ofFloored(
                mc.player.getX() + forwardX * 0.6,
                mc.player.getY() + y,
                mc.player.getZ() + forwardZ * 0.6
            );
            
            if (!mc.world.getBlockState(checkPos).isAir()) {
                net.minecraft.util.math.BlockPos abovePos = checkPos.up();
                if (mc.world.getBlockState(abovePos).isAir()) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private void doGrimStep() {
        if (isStepping) return;
        
        isStepping = true;
        startY = mc.player.getY();
        stepStage = 0;
        
        // Устанавливаем шаг в атрибут (для плавности)
        setStepHeight(height.getValue());
        
        // Сбрасываем таймер
        stepTimer.reset();
    }
    
    private void doMatrixStep() {
        if (isStepping) return;
        
        // Для Matrix просто увеличиваем шаг, он сам обработает
        setStepHeight(height.getValue());
        stepTimer.reset();
    }
    
    private double[] getGrimOffsets(double h) {
        // Оффсеты для плавного подъема на нужную высоту
        if (h <= 1.0) {
            return new double[]{0.42, 0.75, 1.0};
        } else if (h <= 1.5) {
            return new double[]{0.42, 0.75, 1.0, 1.2, 1.5};
        } else if (h <= 2.0) {
            return new double[]{0.42, 0.78, 0.63, 0.51, 0.9, 1.21, 1.45, 1.8, 2.0};
        }
        return new double[]{0.42, 0.75, 1.0, 1.2, 1.5, 1.8, 2.0};
    }

    private void setStepHeight(float v) {
        if (mc.player != null && mc.player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT) != null) {
            mc.player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT).setBaseValue(v);
        }
    }
}
