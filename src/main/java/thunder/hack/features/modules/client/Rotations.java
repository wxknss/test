package thunder.hack.features.modules.client;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.EventFixVelocity;
import thunder.hack.events.impl.EventKeyboardInput;
import thunder.hack.events.impl.EventPlayerJump;
import thunder.hack.events.impl.EventPlayerTravel;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.combat.Aura;
import thunder.hack.setting.Setting;

public class Rotations extends Module {
    public Rotations() {
        super("Rotations", Category.CLIENT);
    }

    private final Setting<MoveFix> moveFix = new Setting<>("MoveFix", MoveFix.Off);
    public final Setting<Boolean> clientLook = new Setting<>("ClientLook", false);
    private final Setting<Boolean> onlyOnAttack = new Setting<>("OnlyOnAttack", true, v -> moveFix.getValue() == MoveFix.Silent);

    private enum MoveFix {
        Off,           // Выключен
        Focused,       // Подмена yaw в EventPlayerTravel
        Free,          // Изменение вектора движения
        New,           // Без изменения yaw, через input
        Silent,        // Бездействие во время атаки (просто не двигаемся)
        Packet,        // Подмена пакетов движения
        Strafe,        // Страфе исправление через velocity
        Predict        // Предсказание направления
    }

    public float fixRotation;
    private float prevYaw, prevPitch;
    private float lastFixRotation;
    private boolean wasAttacking;

    // Для Packet режима
    private float serverYaw;
    private boolean needSync;

    // Для Predict режима
    private Vec3d predictedMotion;

    public void onJump(EventPlayerJump e) {
        if (Float.isNaN(fixRotation) || moveFix.getValue() == MoveFix.Off || mc.player.isRiding())
            return;

        // Silent и Packet не трогают прыжок
        if (moveFix.getValue() == MoveFix.Silent || moveFix.getValue() == MoveFix.Packet) 
            return;

        if (e.isPre()) {
            prevYaw = mc.player.getYaw();
            mc.player.setYaw(fixRotation);
        } else mc.player.setYaw(prevYaw);
    }

    public void onPlayerMove(EventFixVelocity event) {
        MoveFix mode = moveFix.getValue();
        if (Float.isNaN(fixRotation) || mc.player.isRiding())
            return;

        switch (mode) {
            case Free:
                event.setVelocity(fix(fixRotation, event.getMovementInput(), event.getSpeed()));
                break;

            case New:
                applyNewMode(event);
                break;

            case Strafe:
                applyStrafeMode(event);
                break;

            case Predict:
                applyPredictMode(event);
                break;

            case Silent:
                // В режиме Silent во время атаки просто не двигаемся
                if (ModuleManager.aura.isEnabled() && ModuleManager.aura.target != null) {
                    event.setVelocity(Vec3d.ZERO);
                    event.cancel();
                }
                break;
        }
    }

    private void applyNewMode(EventFixVelocity event) {
        float forward = mc.player.input.movementForward;
        float sideways = mc.player.input.movementSideways;
        
        float yawDelta = fixRotation - mc.player.getYaw();
        float rad = yawDelta * MathHelper.RADIANS_PER_DEGREE;
        float cos = MathHelper.cos(rad);
        float sin = MathHelper.sin(rad);
        
        event.setVelocity(new Vec3d(
            sideways * sin + forward * cos,
            0,
            sideways * cos - forward * sin
        ).normalize().multiply(event.getSpeed()));
    }

    private void applyStrafeMode(EventFixVelocity event) {
        // Страфе режим - исправляем движение через strafe формулу
        float yaw = fixRotation;
        
        if (mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0) {
            float forward = mc.player.input.movementForward;
            float strafe = mc.player.input.movementSideways;
            
            // Конвертируем в движение относительно yaw
            float f = forward * forward + strafe * strafe;
            if (f >= 1.0E-4F) {
                f = MathHelper.sqrt(f);
                if (f < 1.0F) f = 1.0F;
                
                f = event.getSpeed() / f;
                forward *= f;
                strafe *= f;
                
                float sin = MathHelper.sin(yaw * 0.017453292F);
                float cos = MathHelper.cos(yaw * 0.017453292F);
                
                event.setVelocity(new Vec3d(
                    forward * cos - strafe * sin,
                    0,
                    forward * sin + strafe * cos
                ));
            }
        }
    }

    private void applyPredictMode(EventFixVelocity event) {
        // Предсказываем куда должен двигаться игрок при fixRotation
        float yawDiff = fixRotation - mc.player.getYaw();
        
        // Если разница небольшая - просто используем New режим
        if (Math.abs(yawDiff) < 45) {
            applyNewMode(event);
            return;
        }
        
        // Иначе предсказываем что игрок будет нажимать
        predictedMotion = event.getMovementInput();
        event.setVelocity(fix(fixRotation, predictedMotion, event.getSpeed()));
    }

    public void modifyVelocity(EventPlayerTravel e) {
        MoveFix mode = moveFix.getValue();
        
        // Аура с элитрами
        if (ModuleManager.aura.isEnabled() && ModuleManager.aura.target != null && ModuleManager.aura.rotationMode.not(Aura.Mode.None)
                && ModuleManager.aura.elytraTarget.getValue() && Managers.PLAYER.ticksElytraFlying > 5) {
            if (e.isPre()) {
                prevYaw = mc.player.getYaw();
                prevPitch = mc.player.getPitch();
                mc.player.setYaw(fixRotation);
                mc.player.setPitch(ModuleManager.aura.rotationPitch);
            } else {
                mc.player.setYaw(prevYaw);
                mc.player.setPitch(prevPitch);
            }
            return;
        }

        // Разные режимы по-разному обрабатывают Travel
        switch (mode) {
            case Focused:
                if (!Float.isNaN(fixRotation) && !mc.player.isRiding()) {
                    if (e.isPre()) {
                        prevYaw = mc.player.getYaw();
                        mc.player.setYaw(fixRotation);
                    } else {
                        mc.player.setYaw(prevYaw);
                    }
                }
                break;
                
            case Packet:
                handlePacketMode(e);
                break;
                
            case Silent:
                // В Silent вообще не трогаем Travel
                break;
                
            case Strafe:
            case Predict:
                if (!Float.isNaN(fixRotation) && !mc.player.isRiding()) {
                    if (e.isPre()) {
                        prevYaw = mc.player.getYaw();
                        mc.player.setYaw(fixRotation);
                    } else {
                        mc.player.setYaw(prevYaw);
                    }
                }
                break;
        }
    }

    private void handlePacketMode(EventPlayerTravel e) {
        if (Float.isNaN(fixRotation) || mc.player.isRiding()) return;
        
        if (e.isPre()) {
            // Сохраняем реальный yaw
            serverYaw = mc.player.getYaw();
            
            // Если атакуем - не меняем yaw вообще
            if (ModuleManager.aura.isEnabled() && ModuleManager.aura.target != null) {
                // Просто игнорируем изменение
                return;
            }
            
            // Иначе меняем для движения
            mc.player.setYaw(fixRotation);
        } else {
            // Возвращаем оригинальный yaw
            mc.player.setYaw(serverYaw);
        }
    }

    public void onKeyInput(EventKeyboardInput e) {
        MoveFix mode = moveFix.getValue();
        if (Float.isNaN(fixRotation) || mc.player.isRiding())
            return;

        switch (mode) {
            case Free:
                applyFreeInput();
                break;
                
            case New:
                applyNewInput();
                break;
                
            case Silent:
                applySilentInput();
                break;
                
            case Packet:
                applyPacketInput();
                break;
                
            case Predict:
                applyPredictInput();
                break;
                
            case Strafe:
                // Strafe не трогает input
                break;
        }
    }

    private void applyFreeInput() {
        float mF = mc.player.input.movementForward;
        float mS = mc.player.input.movementSideways;
        float delta = (mc.player.getYaw() - fixRotation) * MathHelper.RADIANS_PER_DEGREE;
        float cos = MathHelper.cos(delta);
        float sin = MathHelper.sin(delta);
        mc.player.input.movementSideways = Math.round(mS * cos - mF * sin);
        mc.player.input.movementForward = Math.round(mF * cos + mS * sin);
    }

    private void applyNewInput() {
        float forward = mc.player.input.movementForward;
        float sideways = mc.player.input.movementSideways;
        
        if (forward == 0 && sideways == 0) return;
        
        float yawDelta = fixRotation - mc.player.getYaw();
        float rad = yawDelta * MathHelper.RADIANS_PER_DEGREE;
        float cos = MathHelper.cos(rad);
        float sin = MathHelper.sin(rad);
        
        mc.player.input.movementForward = forward * cos + sideways * sin;
        mc.player.input.movementSideways = sideways * cos - forward * sin;
        
        // Отключаем спринт если движемся назад относительно fixRotation
        if (forward > 0 && Math.abs(yawDelta) > 90) {
            mc.player.setSprinting(false);
        }
    }

    private void applySilentInput() {
        // В режиме Silent при атаке полностью блокируем input
        if (onlyOnAttack.getValue() && ModuleManager.aura.isEnabled() && ModuleManager.aura.target != null) {
            mc.player.input.movementForward = 0;
            mc.player.input.movementSideways = 0;
            mc.player.setSprinting(false);
            return;
        }
        
        // Без атаки работаем как New
        applyNewInput();
    }

    private void applyPacketInput() {
        // В Packet режиме не меняем input вообще
        // Движение будет исправляться через пакеты
    }

    private void applyPredictInput() {
        // Предсказываем что игрок нажмёт и корректируем
        float forward = mc.player.input.movementForward;
        float sideways = mc.player.input.movementSideways;
        
        // Если игрок пытается идти вперёд, но fixRotation смотрит назад
        if (forward > 0) {
            float yawDiff = fixRotation - mc.player.getYaw();
            
            // При большой разнице - инвертируем управление
            if (Math.abs(yawDiff) > 135) {
                mc.player.input.movementForward = -forward;
                mc.player.input.movementSideways = -sideways;
                mc.player.setSprinting(false);
            } else if (Math.abs(yawDiff) > 90) {
                // При средней - только sideways
                mc.player.input.movementForward = 0;
                mc.player.input.movementSideways = sideways;
                mc.player.setSprinting(false);
            } else {
                applyNewInput();
            }
        } else {
            applyNewInput();
        }
    }

    private Vec3d fix(float yaw, Vec3d movementInput, float speed) {
        double d = movementInput.lengthSquared();
        if (d < 1.0E-7)
            return Vec3d.ZERO;
        Vec3d vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).multiply(speed);
        float f = MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE);
        float g = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE);
        return new Vec3d(vec3d.x * (double) g - vec3d.z * (double) f, vec3d.y, vec3d.z * (double) g + vec3d.x * (double) f);
    }

    @Override
    public boolean isToggleable() {
        return false;
    }
}
