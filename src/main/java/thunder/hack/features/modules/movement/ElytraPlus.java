package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import thunder.hack.ThunderHack;
import thunder.hack.core.Managers;
import thunder.hack.events.impl.*;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.MovementUtility;

public class ElytraPlus extends Module {
    public enum Mode { Boost, SunriseNew, Packet }
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Boost);

    // Boost settings
    private final Setting<Float> factor = new Setting<>("Factor", 1.8f, 0.1f, 5f, v -> mode.is(Mode.Boost));
    private final Setting<Float> upSpeed = new Setting<>("UpSpeed", 1.2f, 0.1f, 3f, v -> mode.is(Mode.Boost));
    private final Setting<Float> downFactor = new Setting<>("Glide", 0.8f, 0f, 2f, v -> mode.is(Mode.Boost));
    private final Setting<Boolean> cruiseControl = new Setting<>("CruiseControl", true, v -> mode.is(Mode.Boost));
    private final Setting<Float> minUpSpeed = new Setting<>("MinUpSpeed", 0.8f, 0.1f, 3f, v -> mode.is(Mode.Boost) && cruiseControl.getValue());
    private final Setting<Boolean> instantFly = new Setting<>("InstantFly", true, v -> mode.is(Mode.Boost));
    private final Setting<Float> redeployDelay = new Setting<>("RedeployDelay", 1.2f, 0.5f, 3f, v -> mode.is(Mode.Boost) && instantFly.getValue());
    private final Setting<Boolean> grimBypass = new Setting<>("GrimBypass", true, v -> mode.is(Mode.Boost));

    // SunriseNew settings
    private final Setting<Float> xzSpeed = new Setting<>("XZSpeed", 1.8f, 0.5f, 5f, v -> mode.is(Mode.SunriseNew));
    private final Setting<Float> ySpeed = new Setting<>("YSpeed", 0.5f, 0.1f, 2f, v -> mode.is(Mode.SunriseNew));
    private final Setting<Integer> disablerDelay = new Setting<>("DisablerDelay", 2, 0, 10, v -> mode.is(Mode.SunriseNew));

    // Common
    private final Setting<Boolean> autoToggle = new Setting<>("AutoToggle", false);

    private final Timer startTimer = new Timer();
    private final Timer redeployTimer = new Timer();
    private final Timer glideTimer = new Timer();
    private float acceleration = 0;
    private int disablerTicks = 0;
    private boolean hasTouchedGround = true;
    private float height;

    public ElytraPlus() {
        super("Elytra+", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        acceleration = 0;
        hasTouchedGround = true;
        height = (float) mc.player.getY();
        startTimer.reset();
        redeployTimer.reset();
        if (mode.is(Mode.SunriseNew) && InventoryUtility.getElytra() == -1) {
            disable("No elytra!");
        }
    }

    @EventHandler
    public void onMove(EventMove e) {
        if (mode.is(Mode.Boost)) doBoost(e);
        else if (mode.is(Mode.SunriseNew)) doSunriseNew(e);
        else if (mode.is(Mode.Packet)) doPacket(e);
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (mode.is(Mode.Boost) && instantFly.getValue()) {
            if (mc.player.isOnGround()) hasTouchedGround = true;
            if (!mc.player.isFallFlying() && hasTouchedGround && !mc.player.isOnGround() && mc.player.fallDistance > 0.2) {
                if (startTimer.passedMs((long) (redeployDelay.getValue() * 1000))) {
                    // Grim bypass: имитация прыжка перед стартом
                    if (grimBypass.getValue()) {
                        sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_JUMP));
                    }
                    sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    startTimer.reset();
                    hasTouchedGround = false;
                }
            }
        }
        if (mode.is(Mode.SunriseNew)) {
            if (mc.player.isOnGround()) {
                mc.player.jump();
                acceleration = 0;
            }
            if (disablerTicks-- <= 0) {
                matrixDisabler();
                disablerTicks = disablerDelay.getValue();
            }
        }
    }

    private void doBoost(EventMove e) {
        if (mc.player.getInventory().getStack(38).getItem() != Items.ELYTRA || !mc.player.isFallFlying()) return;

        if (cruiseControl.getValue()) {
            if (mc.options.jumpKey.isPressed()) height += 0.5f;
            if (mc.options.sneakKey.isPressed()) height -= 0.5f;

            double heightPct = 1 - Math.sqrt(MathHelper.clamp(Managers.PLAYER.currentPlayerSpeed / 1.7, 0.0, 1.0));
            if (Managers.PLAYER.currentPlayerSpeed >= minUpSpeed.getValue() && startTimer.passedMs(1500)) {
                double pitch = -(44.4 * heightPct + 0.6);
                double diff = (height + 1 - mc.player.getY()) * 2;
                double pDist = -Math.toDegrees(Math.atan2(Math.abs(diff), Managers.PLAYER.currentPlayerSpeed * 30.0)) * Math.signum(diff);
                mc.player.setPitch((float) (pitch + (pDist - pitch) * MathHelper.clamp(Math.abs(diff), 0.0, 1.0)));
            } else {
                mc.player.setPitch(0.25F);
            }
        }

        Vec3d look = mc.player.getRotationVec(1f);
        double currentSpeed = Math.hypot(e.getX(), e.getZ());
        double d6 = Math.hypot(look.x, look.z);

        // Ускорение при взгляде вниз (pitch > 0)
        if (mc.player.getPitch() > 0 && e.getY() < 0) {
            if (MovementUtility.isMoving() && startTimer.passedMs(1500) && redeployTimer.passedMs(3000)) {
                startTimer.reset();
                sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            } else if (!startTimer.passedMs(1500)) {
                float move = mc.player.input.movementForward;
                e.setX(e.getX() - move * Math.sin(Math.toRadians(mc.player.getYaw())) * factor.getValue() / 20f);
                e.setZ(e.getZ() + move * Math.cos(Math.toRadians(mc.player.getYaw())) * factor.getValue() / 20f);
                redeployTimer.reset();
            }
        }

        // Торможение при взгляде вверх (pitch < 0)
        if (mc.player.getPitch() < 0) {
            double ySpeed = currentSpeed * -Math.sin(Math.toRadians(mc.player.getPitch())) * 0.04;
            e.setY(e.getY() + ySpeed * 3.2);
            e.setX(e.getX() - look.x * ySpeed / d6);
            e.setZ(e.getZ() - look.z * ySpeed / d6);
        }

        // Горизонтальное ускорение
        if (d6 > 0) {
            e.setX(e.getX() + (look.x / d6 * currentSpeed - e.getX()) * 0.1);
            e.setZ(e.getZ() + (look.z / d6 * currentSpeed - e.getZ()) * 0.1);
        }

        // Вертикальное управление
        if (mc.options.jumpKey.isPressed()) e.setY(upSpeed.getValue());
        else if (mc.options.sneakKey.isPressed()) e.setY(-upSpeed.getValue());
        else e.setY(-0.08 * downFactor.getValue());

        mc.player.setVelocity(e.getX(), e.getY(), e.getZ());
        e.cancel();
    }

    private void doSunriseNew(EventMove e) {
        if (mc.player.getInventory().getStack(38).getItem() != Items.ELYTRA) return;
        if (!mc.player.isFallFlying()) {
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return;
        }

        if (mc.player.horizontalCollision) acceleration = 0;

        MovementUtility.setMotion(Math.min((acceleration += 8f / xzSpeed.getValue()) / 100f, xzSpeed.getValue()));
        if (!MovementUtility.isMoving()) acceleration = 0;

        if (mc.options.jumpKey.isPressed()) e.setY(ySpeed.getValue());
        else if (mc.options.sneakKey.isPressed()) e.setY(-ySpeed.getValue());
        else e.setY(-0.005f);

        mc.player.setVelocity(e.getX(), e.getY(), e.getZ());
        e.cancel();
    }

    private void doPacket(EventMove e) {
        if (mc.player.getInventory().getStack(38).getItem() != Items.ELYTRA) return;
        if (!mc.player.isFallFlying())
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));

        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().setFlySpeed(xzSpeed.getValue() / 15f);
        e.cancel();

        if (mc.options.jumpKey.isPressed()) e.setY(ySpeed.getValue());
        else if (mc.options.sneakKey.isPressed()) e.setY(-ySpeed.getValue());
        else e.setY(0);

        mc.player.setVelocity(e.getX(), e.getY(), e.getZ());
    }

    private void matrixDisabler() {
        int elytra = InventoryUtility.getElytra();
        if (elytra == -1) return;
        elytra = elytra < 9 ? elytra + 36 : elytra;
        // Быстрая смена брони для сбивания предсказания Grim
        clickSlot(elytra);
        clickSlot(6);
        clickSlot(elytra);
        sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
    }

    private void clickSlot(int slot) {
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    @Override
    public void onDisable() {
        ThunderHack.TICK_TIMER = 1.0f;
        mc.player.getAbilities().flying = false;
        mc.player.getAbilities().setFlySpeed(0.05f);
    }
}
