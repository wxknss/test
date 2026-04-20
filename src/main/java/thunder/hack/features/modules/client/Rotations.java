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

    public enum MoveFixMode {
        Off, Focused, Free, New, Strafe, Silent, Grim
    }

    private final Setting<MoveFixMode> moveFix = new Setting<>("MoveFix", MoveFixMode.Off);
    public final Setting<Boolean> clientLook = new Setting<>("ClientLook", false);
    public final Setting<Boolean> onlyOnAttack = new Setting<>("OnlyOnAttack", true, v -> moveFix.getValue() == MoveFixMode.Silent);

    public float fixRotation;
    private float prevYaw, prevPitch;
    private boolean wasAttacking;

    // --- Event handlers ---
    public void onJump(EventPlayerJump e) {
        if (Float.isNaN(fixRotation) || moveFix.getValue() == MoveFixMode.Off || mc.player.isRiding())
            return;
        if (moveFix.getValue() == MoveFixMode.Silent || moveFix.getValue() == MoveFixMode.Grim) return;

        if (e.isPre()) {
            prevYaw = mc.player.getYaw();
            mc.player.setYaw(fixRotation);
        } else {
            mc.player.setYaw(prevYaw);
        }
    }

    public void onPlayerMove(EventFixVelocity event) {
        MoveFixMode mode = moveFix.getValue();
        if (Float.isNaN(fixRotation) || mc.player.isRiding()) return;

        switch (mode) {
            case Free:
                event.setVelocity(fix(fixRotation, event.getMovementInput(), event.getSpeed()));
                break;
            case New:
            case Strafe:
                applyStrafeFix(event, mode == MoveFixMode.New);
                break;
            case Silent:
                if (onlyOnAttack.getValue() && ModuleManager.aura.isEnabled() && ModuleManager.aura.target != null) {
                    event.setVelocity(Vec3d.ZERO);
                    event.cancel();
                }
                break;
            case Grim:
                // Grim fix: не меняем velocity, только корректируем input
                break;
        }
    }

    public void modifyVelocity(EventPlayerTravel e) {
        MoveFixMode mode = moveFix.getValue();

        // Aura with elytra
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

        if (mode == MoveFixMode.Focused && !Float.isNaN(fixRotation) && !mc.player.isRiding()) {
            if (e.isPre()) {
                prevYaw = mc.player.getYaw();
                mc.player.setYaw(fixRotation);
            } else {
                mc.player.setYaw(prevYaw);
            }
        }

        // Grim: не трогаем yaw в Travel
    }

    public void onKeyInput(EventKeyboardInput e) {
        MoveFixMode mode = moveFix.getValue();
        if (Float.isNaN(fixRotation) || mc.player.isRiding()) return;

        switch (mode) {
            case Free:
                applyFreeInput();
                break;
            case New:
            case Strafe:
                applyNewInput();
                break;
            case Silent:
                if (onlyOnAttack.getValue() && ModuleManager.aura.isEnabled() && ModuleManager.aura.target != null) {
                    mc.player.input.movementForward = 0;
                    mc.player.input.movementSideways = 0;
                    mc.player.setSprinting(false);
                    return;
                }
                applyNewInput();
                break;
            case Grim:
                applyGrimInput();
                break;
        }
    }

    // --- Helper methods ---
    private void applyStrafeFix(EventFixVelocity event, boolean useInput) {
        float forward = mc.player.input.movementForward;
        float sideways = mc.player.input.movementSideways;
        if (forward == 0 && sideways == 0) return;

        float yawDelta = fixRotation - mc.player.getYaw();
        float rad = yawDelta * MathHelper.RADIANS_PER_DEGREE;
        float cos = MathHelper.cos(rad);
        float sin = MathHelper.sin(rad);

        Vec3d motion = new Vec3d(
                sideways * sin + forward * cos,
                0,
                sideways * cos - forward * sin
        ).normalize().multiply(event.getSpeed());

        event.setVelocity(motion);
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

        if (forward > 0 && Math.abs(yawDelta) > 90) {
            mc.player.setSprinting(false);
        }
    }

    private void applyGrimInput() {
        // Grim expects input to match rotation; we do nothing here, handled in EventFixVelocity via Strafe logic
        applyNewInput();
    }

    private Vec3d fix(float yaw, Vec3d movementInput, float speed) {
        double d = movementInput.lengthSquared();
        if (d < 1.0E-7) return Vec3d.ZERO;
        Vec3d vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).multiply(speed);
        float f = MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE);
        float g = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE);
        return new Vec3d(vec3d.x * g - vec3d.z * f, vec3d.y, vec3d.z * g + vec3d.x * f);
    }

    @Override
    public boolean isToggleable() {
        return false;
    }
}
