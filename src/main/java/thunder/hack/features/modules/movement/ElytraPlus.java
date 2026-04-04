package thunder.hack.features.modules.movement;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import thunder.hack.ThunderHack;
import thunder.hack.core.Managers;
import thunder.hack.events.impl.*;
import thunder.hack.gui.font.FontRenderers;
import thunder.hack.gui.notification.Notification;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.client.HudEditor;
import thunder.hack.features.modules.combat.Criticals;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.Bind;
import thunder.hack.setting.impl.BooleanSettingGroup;
import thunder.hack.utility.math.MathUtility;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.MovementUtility;
import thunder.hack.utility.player.PlayerUtility;
import thunder.hack.utility.render.Render2DEngine;
import thunder.hack.utility.render.Render3DEngine;

import java.util.List;

import static thunder.hack.features.modules.client.ClientSettings.isRu;
import static thunder.hack.features.modules.player.ElytraSwap.getChestPlateSlot;

public class ElytraPlus extends Module {
    public ElytraPlus() {
        super("Elytra+", Category.MOVEMENT);
    }

    public final Setting<Mode> mode = new Setting<>("Mode", Mode.Boost);
    private final Setting<Integer> disablerDelay = new Setting<>("DisablerDelay", 1, 0, 10, v -> mode.is(Mode.SunriseOld));
    private final Setting<Boolean> twoBee = new Setting<>("2b2t", true, v -> mode.is(Mode.Boost));
    private final Setting<Boolean> onlySpace = new Setting<>("OnlySpace", true, v -> mode.is(Mode.Boost) && twoBee.getValue());
    private final Setting<Boolean> stopOnGround = new Setting<>("StopOnGround", false, v -> mode.is(Mode.Packet));
    private final Setting<Boolean> infDurability = new Setting<>("InfDurability", true, v -> mode.is(Mode.Packet));
    private final Setting<Boolean> vertical = new Setting<>("Vertical", false, v -> mode.is(Mode.Packet));
    private final Setting<NCPStrict> ncpStrict = new Setting<>("NCPStrict", NCPStrict.Off, v -> mode.is(Mode.Packet));
    private final Setting<AntiKick> antiKick = new Setting<>("AntiKick", AntiKick.Jitter, v -> mode.is(Mode.FireWork) || mode.is(Mode.SunriseOld));
    private final Setting<Float> xzSpeed = new Setting<>("XZSpeed", 1.55f, 0.1f, 10f, v -> mode.is(Mode.Control));
    private final Setting<Float> ySpeed = new Setting<>("YSpeed", 0.47f, 0f, 2f, v -> mode.is(Mode.FireWork) || mode.getValue() == Mode.SunriseOld || (mode.is(Mode.Packet) && vertical.getValue()));
    private final Setting<Integer> fireSlot = new Setting<>("FireSlot", 1, 1, 9, v -> mode.is(Mode.FireWork));
    private final Setting<BooleanSettingGroup> accelerate = new Setting<>("Acceleration", new BooleanSettingGroup(false), v -> mode.is(Mode.Control) || mode.is(Mode.Packet));
    private final Setting<Float> accelerateFactor = new Setting<>("AccelerateFactor", 9f, 0f, 100f, v -> mode.is(Mode.Control) || mode.is(Mode.Packet)).addToGroup(accelerate);
    private final Setting<Float> fireDelay = new Setting<>("FireDelay", 1.5f, 0f, 1.5f, v -> mode.is(Mode.FireWork));
    private final Setting<BooleanSettingGroup> grim = new Setting<>("Grim", new BooleanSettingGroup(false), v -> mode.is(Mode.FireWork));
    private final Setting<Boolean> rotate = new Setting<>("Rotate", true, v -> mode.is(Mode.FireWork)).addToGroup(grim);
    private final Setting<Boolean> fireWorkExtender = new Setting<>("FireWorkExtender", true, v -> mode.is(Mode.FireWork)).addToGroup(grim);
    private final Setting<Boolean> stayMad = new Setting<>("GroundSafe", false, v -> mode.is(Mode.FireWork));
    private final Setting<Boolean> keepFlying = new Setting<>("KeepFlying", false, v -> mode.is(Mode.FireWork));
    private final Setting<Boolean> disableOnFlag = new Setting<>("DisableOnFlag", false, v -> mode.is(Mode.FireWork));
    private final Setting<Boolean> allowFireSwap = new Setting<>("AllowFireSwap", false, v -> mode.is(Mode.FireWork));
    private final Setting<Boolean> bowBomb = new Setting<>("BowBomb", false, v -> mode.is(Mode.FireWork) || mode.getValue() == Mode.SunriseOld);
    private final Setting<Bind> bombKey = new Setting<>("BombKey", new Bind(-1, false, false), v -> mode.getValue() == Mode.SunriseOld);
    private final Setting<Boolean> instantFly = new Setting<>("InstantFly", true, v -> ((mode.is(Mode.Boost) && !twoBee.getValue()) || mode.is(Mode.Control)));
    private final Setting<Boolean> cruiseControl = new Setting<>("CruiseControl", false, v -> mode.is(Mode.Boost));
    private final Setting<Float> factor = new Setting<>("Factor", 0.85f, 0.1f, 50.0f, v -> mode.is(Mode.Boost));
    private final Setting<Float> upSpeed = new Setting<>("UpSpeed", 1.0f, 0.01f, 5.0f, v -> ((mode.is(Mode.Boost) && !twoBee.getValue()) || mode.is(Mode.Control)));
    private final Setting<Float> downFactor = new Setting<>("Glide", 1.0f, 0.0f, 2.0f, v -> ((mode.is(Mode.Boost) && !twoBee.getValue()) || mode.is(Mode.Control)));
    private final Setting<Boolean> stopMotion = new Setting<>("StopMotion", true, v -> mode.is(Mode.Boost) && !twoBee.getValue());
    private final Setting<Float> minUpSpeed = new Setting<>("MinUpSpeed", 0.5f, 0.1f, 5.0f, v -> mode.is(Mode.Boost) && cruiseControl.getValue());
    private final Setting<Boolean> forceHeight = new Setting<>("ForceHeight", false, v -> (mode.is(Mode.Boost) && cruiseControl.getValue()));
    private final Setting<Integer> manualHeight = new Setting<>("Height", 121, 1, 256, v -> mode.is(Mode.Boost) && forceHeight.getValue());
    private final Setting<Float> sneakDownSpeed = new Setting<>("DownSpeed", 1.0f, 0.01f, 5.0f, v -> mode.is(Mode.Control));
    private final Setting<Boolean> speedLimit = new Setting<>("SpeedLimit", true, v -> mode.is(Mode.Boost));
    private final Setting<Float> maxSpeed = new Setting<>("MaxSpeed", 10.0f, 0.1f, 20.0f, v -> mode.is(Mode.Boost));
    private final Setting<Float> redeployInterval = new Setting<>("RedeployInterval", 1F, 0.1F, 5F, v -> mode.is(Mode.Boost) && !twoBee.getValue());
    private final Setting<Float> redeployTimeOut = new Setting<>("RedeployTimeout", 5f, 0.1f, 20f, v -> mode.is(Mode.Boost) && !twoBee.getValue());
    private final Setting<Float> redeployDelay = new Setting<>("RedeployDelay", 0.5F, 0.1F, 1F, v -> mode.is(Mode.Boost) && !twoBee.getValue());
    private final Setting<Float> infiniteMaxSpeed = new Setting<>("InfiniteMaxSpeed", 150f, 50f, 170f, v -> mode.getValue() == Mode.Pitch40Infinite);
    private final Setting<Float> infiniteMinSpeed = new Setting<>("InfiniteMinSpeed", 25f, 10f, 70f, v -> mode.getValue() == Mode.Pitch40Infinite);
    private final Setting<Integer> infiniteMaxHeight = new Setting<>("InfiniteMaxHeight", 200, 50, 360, v -> mode.getValue() == Mode.Pitch40Infinite);
    
    // ===== НАСТРОЙКИ ДЛЯ ОБХОДА GRIMAC =====
    private final Setting<Integer> boostDelay = new Setting<>("BoostDelay", 120, 50, 500, v -> mode.is(Mode.Boost));
    private final Setting<Boolean> jumpBeforeGlide = new Setting<>("JumpBeforeGlide", true, v -> mode.is(Mode.Boost));
    private final Setting<Integer> jumpDelay = new Setting<>("JumpDelay", 100, 50, 300, v -> jumpBeforeGlide.getValue() && mode.is(Mode.Boost));
    private final Setting<Boolean> antiRubberband = new Setting<>("AntiRubberband", true);
    private final Setting<Boolean> positionSync = new Setting<>("PositionSync", true);
    private final Setting<Boolean> pitchLimit = new Setting<>("PitchLimit", true);
    private final Setting<Integer> maxPitch = new Setting<>("MaxPitch", 89, 80, 90, v -> pitchLimit.getValue());
    private final Setting<Integer> minPitch = new Setting<>("MinPitch", -89, -90, -80, v -> pitchLimit.getValue());

    public enum Mode {Boost, Control, FireWork, Packet, SunriseOld, SunriseNew, Pitch40Infinite}
    public enum AntiKick {Off, Jitter, Glide}
    public enum NCPStrict {Off, Old, New, Motion}

    private final thunder.hack.utility.Timer startTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer redeployTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer strictTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer pingTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer boostCooldownTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer jumpTimer = new thunder.hack.utility.Timer();
    
    private boolean waitingForConfirm = false;
    private boolean hasJumpedForGlide = false;
    private Vec3d lastConfirmedPos = null;

    private boolean infiniteFlag, hasTouchedGround, elytraEquiped, flying, started;
    private float acceleration, accelerationY, height, prevClientPitch, infinitePitch, lastInfinitePitch;
    private ItemStack prevArmorItemCopy, getStackInSlotCopy;
    private Item prevArmorItem = Items.AIR;
    private Item prevItemInHand = Items.AIR;
    private Vec3d flightZonePos;
    private int prevElytraSlot = -1, disablerTicks;
    private int slotWithFireWorks = -1;
    private long lastFireworkTime;

    @Override
    public void onEnable() {
        flying = false;
        infiniteFlag = false;
        acceleration = 0;
        accelerationY = 0;
        hasJumpedForGlide = false;
        lastConfirmedPos = null;

        if (mc.player != null)
            height = (float) mc.player.getY();

        pingTimer.reset();
        boostCooldownTimer.reset();
        jumpTimer.reset();

        if (mode.is(Mode.FireWork)) fireworkOnEnable();
    }

    @EventHandler
    public void modifyVelocity(EventTravel e) {
        if (mode.getValue() == Mode.Pitch40Infinite) {
            if (e.isPre()) {
                prevClientPitch = mc.player.getPitch();
                mc.player.setPitch(lastInfinitePitch);
            } else mc.player.setPitch(prevClientPitch);
        }
        if (mode.is(Mode.FireWork)) {
            if (Managers.PLAYER.ticksElytraFlying < 4) {
                if (e.isPre()) {
                    prevClientPitch = mc.player.getPitch();
                    mc.player.setPitch(-45f);
                } else mc.player.setPitch(prevClientPitch);
            }
        }
        if (mode.getValue() == Mode.SunriseNew) {
            if (mc.options.jumpKey.isPressed()) {
                if (e.isPre()) {
                    prevClientPitch = mc.player.getPitch();
                    mc.player.setPitch(-45f);
                } else mc.player.setPitch(prevClientPitch);
            } else if (mc.options.sneakKey.isPressed()) {
                if (e.isPre()) {
                    prevClientPitch = mc.player.getPitch();
                    mc.player.setPitch(45f);
                } else mc.player.setPitch(prevClientPitch);
            }
        }
        
        if (mode.is(Mode.Boost) && pitchLimit.getValue()) {
            if (e.isPre()) {
                prevClientPitch = mc.player.getPitch();
                float clampedPitch = MathHelper.clamp(mc.player.getPitch(), minPitch.getValue(), maxPitch.getValue());
                mc.player.setPitch(clampedPitch);
            } else {
                mc.player.setPitch(prevClientPitch);
            }
        }
    }

    @EventHandler
    public void onSync(EventSync e) {
        switch (mode.getValue()) {
            case SunriseOld -> doSunrise();
            case SunriseNew -> doSunriseNew();
            case Boost, Control -> doPreLegacy();
            case FireWork -> fireworkOnSync();
            case Pitch40Infinite -> doPitch40Infinite();
            case Packet -> doPacket(e);
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (e.getPacket() instanceof PlayerPositionLookS2CPacket) {
            acceleration = 0;
            accelerationY = 0;
            pingTimer.reset();
            
            if (positionSync.getValue()) {
                lastConfirmedPos = mc.player.getPos();
            }
            
            if (mode.is(Mode.Boost) && waitingForConfirm) {
                waitingForConfirm = false;
                boostCooldownTimer.reset();
            }
        }
    }

    @EventHandler
    public void onMove(EventMove e) {
        switch (mode.getValue()) {
            case Boost -> doBoost(e);
            case Control -> doControl(e);
            case FireWork -> fireworkOnMove(e);
            case Packet -> doMotionPacket(e);
        }
    }

    private void doPreLegacy() {
        if (twoBee.getValue() && mode.is(Mode.Boost)) return;
        if (mc.player.isOnGround()) hasTouchedGround = true;
        if (!cruiseControl.getValue()) height = (float) mc.player.getY();

        if (strictTimer.passedMs(1500) && !strictTimer.passedMs(2000))
            ThunderHack.TICK_TIMER = 1.0f;

        if (!mc.player.isFallFlying()) {
            if (hasTouchedGround && !mc.player.isOnGround() && mc.player.fallDistance > 0 && instantFly.getValue())
                ThunderHack.TICK_TIMER = 0.3f;

            if (!mc.player.isOnGround() && instantFly.getValue() && mc.player.getVelocity().getY() < 0D) {
                if (!startTimer.passedMs((long) (1000 * redeployDelay.getValue()))) return;
                startTimer.reset();
                sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                hasTouchedGround = false;
                strictTimer.reset();
            }
        }
    }

    private void doBoost(EventMove e) {
        if (mc.player.getInventory().getStack(38).getItem() != Items.ELYTRA) {
            return;
        }
        
        // ===== ВЗЛЕТ С МЕСТА (обход GrimAC ElytraB) =====
        if (mc.player.isOnGround() && mc.options.jumpKey.isPressed()) {
            if (jumpBeforeGlide.getValue()) {
                if (!hasJumpedForGlide && jumpTimer.passedMs(jumpDelay.getValue())) {
                    mc.player.setVelocity(mc.player.getVelocity().x, 1.2, mc.player.getVelocity().z);
                    mc.player.jump();
                    hasJumpedForGlide = true;
                    jumpTimer.reset();
                } else if (hasJumpedForGlide && jumpTimer.passedMs(jumpDelay.getValue())) {
                    sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    hasJumpedForGlide = false;
                    jumpTimer.reset();
                }
            } else {
                mc.player.setVelocity(mc.player.getVelocity().x, 1.2, mc.player.getVelocity().z);
                mc.player.jump();
                sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
            boostCooldownTimer.reset();
            return;
        }
        
        if (!mc.player.isFallFlying() || mc.player.isTouchingWater() || mc.player.isInLava()) {
            hasJumpedForGlide = false;
            return;
        }
        
        // ===== АНТИ-РУБЕРБЕНД =====
        if (antiRubberband.getValue() && lastConfirmedPos != null && mc.player.getPos().distanceTo(lastConfirmedPos) > 2.0) {
            mc.player.setPosition(lastConfirmedPos.x, lastConfirmedPos.y, lastConfirmedPos.z);
        }
        
        // ===== ЗАДЕРЖКА МЕЖДУ БУСТАМИ =====
        int currentPing = getCurrentPing();
        if (currentPing == 0) currentPing = 140;
        
        long minDelay = Math.min(400, Math.max(80, (long)(currentPing * 0.8)));
        
        if (!boostCooldownTimer.passedMs(minDelay)) {
            return;
        }
        
        // ===== БУСТ =====
        if (twoBee.getValue()) {
            if ((mc.options.jumpKey.isPressed() || !onlySpace.getValue() || cruiseControl.getValue())) {
                double[] m = MovementUtility.forwardWithoutStrafe((factor.getValue() / 10f));
                e.setX(e.getX() + m[0]);
                e.setZ(e.getZ() + m[1]);
            }
        }
        
        // ===== ОГРАНИЧЕНИЕ СКОРОСТИ =====
        double speed = Math.hypot(e.getX(), e.getZ());
        if (speedLimit.getValue() && speed > maxSpeed.getValue()) {
            e.setX(e.getX() * maxSpeed.getValue() / speed);
            e.setZ(e.getZ() * maxSpeed.getValue() / speed);
        }
        
        mc.player.setVelocity(e.getX(), e.getY(), e.getZ());
        e.cancel();
        
        boostCooldownTimer.reset();
        waitingForConfirm = true;
    }

    private void doControl(EventMove e) {
        if (mc.player.getInventory().getStack(38).getItem() != Items.ELYTRA || !mc.player.isFallFlying())
            return;

        double[] dir = MovementUtility.forward(xzSpeed.getValue() * (accelerate.getValue().isEnabled() ? Math.min((acceleration += accelerateFactor.getValue()) / 100.0f, 1.0f) : 1f));
        e.setX(dir[0]);
        e.setY(mc.options.jumpKey.isPressed() ? upSpeed.getValue() : mc.options.sneakKey.isPressed() ? -sneakDownSpeed.getValue() : -0.08 * downFactor.getValue());
        e.setZ(dir[1]);

        if (!MovementUtility.isMoving())
            acceleration = 0;

        mc.player.setVelocity(e.getX(), e.getY(), e.getZ());
        e.cancel();
    }

    private int getCurrentPing() {
        if (mc.getNetworkHandler() == null) return 0;
        var playerEntry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        if (playerEntry == null) return 0;
        return playerEntry.getLatency();
    }

    // ===== ЗАГЛУШКИ ДЛЯ ОСТАЛЬНЫХ МЕТОДОВ (оригинал из ThunderHack) =====
    private void doPacket(EventSync e) {}
    private void doPitch40Infinite() {}
    private void doSunriseNew() {}
    private void doSunrise() {}
    private void doMotionPacket(EventMove e) {}
    private void fireworkOnMove(EventMove e) {}
    private void fireworkOnSync() {}
    private void fireworkOnEnable() {}
    private void fireworkOnDisable() {}
    private void fireWorkOnPlayerUpdate() {}
    private void doFireWork(boolean started) {}
    
    public void onRender3D(MatrixStack stack) {}
    public void onRender2D(DrawContext context) {}
}
