package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import thunder.hack.ThunderHack;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.EventSync;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.MovementUtility;
import thunder.hack.utility.world.HoleUtility;

public class Step extends Module {
    private final Setting<Boolean> strict = new Setting<>("Strict", false);
    private final Setting<Float> height = new Setting<>("Height", 2.0F, 1F, 2.5F, v -> !strict.getValue());
    private final Setting<Boolean> useTimer = new Setting<>("Timer", false);
    private final Setting<Boolean> pauseIfShift = new Setting<>("PauseIfShift", false);
    private final Setting<Integer> stepDelay = new Setting<>("StepDelay", 200, 0, 1000);
    private final Setting<Boolean> holeDisable = new Setting<>("HoleDisable", false);
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.NCP);

    private final Timer stepTimer = new Timer();
    private final Timer packetTimer = new Timer();
    private boolean alreadyInHole;
    private boolean timer;
    private boolean stepping;
    private int packetIndex;
    private double[] offsets;
    private double prevX, prevZ;

    public Step() {
        super("Step", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        alreadyInHole = mc.player != null && HoleUtility.isHole(mc.player.getBlockPos());
        stepping = false;
    }

    @Override
    public void onDisable() {
        ThunderHack.TICK_TIMER = 1f;
        setStepHeight(0.6F);
        stepping = false;
    }

    @Override
    public void onUpdate() {
        if (holeDisable.getValue() && HoleUtility.isHole(mc.player.getBlockPos()) && !alreadyInHole) {
            disable("Player in hole... Disabling...");
            return;
        }
        alreadyInHole = mc.player != null && HoleUtility.isHole(mc.player.getBlockPos());

        if (pauseIfShift.getValue() && mc.options.sneakKey.isPressed()) {
            setStepHeight(0.6F);
            return;
        }

        if (mc.player.getAbilities().flying || ModuleManager.freeCam.isOn() || mc.player.isRiding() || mc.player.isTouchingWater()) {
            setStepHeight(0.6F);
            return;
        }

        if (timer && mc.player.isOnGround()) {
            ThunderHack.TICK_TIMER = 1f;
            timer = false;
        }

        if (stepping) {
            sendPackets();
        } else if (mc.player.isOnGround() && stepTimer.passedMs(stepDelay.getValue())) {
            setStepHeight(height.getValue());
        } else {
            setStepHeight(0.6F);
        }
    }

    @EventHandler
    public void onStep(EventSync event) {
        if (mode.getValue() != Mode.NCP) return;

        double stepHeight = mc.player.getY() - mc.player.prevY;
        if (stepHeight <= 0.6 || stepHeight > height.getValue() || (strict.getValue() && stepHeight > 1)) return;

        offsets = getOffset(stepHeight);
        if (offsets == null || offsets.length == 0) return;

        prevX = mc.player.prevX;
        prevZ = mc.player.prevZ;
        packetIndex = 0;
        stepping = true;

        if (useTimer.getValue()) {
            ThunderHack.TICK_TIMER = 1.0f / offsets.length;
            timer = true;
        }

        stepTimer.reset();
    }

    private void sendPackets() {
        if (!stepping || offsets == null) return;

        if (packetIndex < offsets.length) {
            double offset = offsets[packetIndex];
            sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(prevX, prevX + offset, prevZ, false));
            packetIndex++;
            return;
        }

        // Финальный пакет с реальной позицией и горизонтальным движением
        Vec3d motion = new Vec3d(mc.player.getVelocity().x, 0, mc.player.getVelocity().z);
        if (motion.length() < 0.18) {
            double[] strafe = MovementUtility.forward(0.1838601407459074);
            motion = new Vec3d(strafe[0], 0, strafe[1]);
        }

        sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX() + motion.x,
                mc.player.getY() - (mc.player.getY() % 1.0),
                mc.player.getZ() + motion.z,
                true
        ));

        stepping = false;
        offsets = null;
    }

    public double[] getOffset(double h) {
        return switch ((int) (h * 10000)) {
            case 7500, 10000 -> new double[]{0.42, 0.753};
            case 8125, 8750 -> new double[]{0.39, 0.7};
            case 15000 -> new double[]{0.42, 0.75, 1.0, 1.16, 1.23, 1.2};
            case 20000 -> new double[]{0.42, 0.78, 0.63, 0.51, 0.9, 1.21, 1.45, 1.43};
            case 250000 -> new double[]{0.425, 0.821, 0.699, 0.599, 1.022, 1.372, 1.652, 1.869, 2.019, 1.907};
            default -> null;
        };
    }

    private void setStepHeight(float v) {
        mc.player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT).setBaseValue(v);
    }

    public enum Mode { NCP, VANILLA }
}
