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

    // ===== ОСНОВНЫЕ НАСТРОЙКИ =====
    public final Setting<Mode> mode = new Setting<>("Mode", Mode.Boost);
    private final Setting<Boolean> twoBee = new Setting<>("2b2t", true, v -> mode.is(Mode.Boost));
    private final Setting<Boolean> onlySpace = new Setting<>("OnlySpace", true, v -> mode.is(Mode.Boost) && twoBee.getValue());
    private final Setting<Float> factor = new Setting<>("Factor", 0.85f, 0.1f, 50.0f, v -> mode.is(Mode.Boost));
    private final Setting<Boolean> speedLimit = new Setting<>("SpeedLimit", true, v -> mode.is(Mode.Boost));
    private final Setting<Float> maxSpeed = new Setting<>("MaxSpeed", 10.0f, 0.1f, 20.0f, v -> mode.is(Mode.Boost) && speedLimit.getValue());

    // ===== ОБХОДЫ GRIMAC =====
    private final Setting<GrimBypass> grimBypass = new Setting<>("GrimBypass", GrimBypass.Vanilla);
    private final Setting<Integer> boostDelay = new Setting<>("BoostDelay", 120, 50, 500, v -> mode.is(Mode.Boost));
    private final Setting<Boolean> jumpBeforeGlide = new Setting<>("JumpBeforeGlide", true, v -> mode.is(Mode.Boost));
    private final Setting<Integer> jumpDelay = new Setting<>("JumpDelay", 100, 50, 300, v -> jumpBeforeGlide.getValue() && mode.is(Mode.Boost));
    private final Setting<Boolean> antiRubberband = new Setting<>("AntiRubberband", true);
    private final Setting<Boolean> positionSync = new Setting<>("PositionSync", true);
    private final Setting<Boolean> noSlowGlide = new Setting<>("NoSlowGlide", false);
    private final Setting<Boolean> fakeJump = new Setting<>("FakeJump", false);
    private final Setting<Boolean> elytraBypass = new Setting<>("ElytraBypass", true);
    private final Setting<Boolean> pitchLimit = new Setting<>("PitchLimit", true);
    private final Setting<Integer> maxPitch = new Setting<>("MaxPitch", 89, 80, 90, v -> pitchLimit.getValue());
    private final Setting<Integer> minPitch = new Setting<>("MinPitch", -89, -90, -80, v -> pitchLimit.getValue());
    private final Setting<Boolean> noGlideOnGround = new Setting<>("NoGlideOnGround", true);
    private final Setting<Boolean> requireJump = new Setting<>("RequireJump", true);
    private final Setting<Boolean> syncOnTeleport = new Setting<>("SyncOnTeleport", true);
    private final Setting<Integer> syncDelay = new Setting<>("SyncDelay", 2, 0, 10, v -> syncOnTeleport.getValue());

    // ===== ДОПОЛНИТЕЛЬНЫЕ РЕЖИМЫ =====
    private final Setting<Boolean> autoRecast = new Setting<>("AutoRecast", false);
    private final Setting<Integer> recastDelay = new Setting<>("RecastDelay", 1000, 100, 5000, v -> autoRecast.getValue());
    private final Setting<Boolean> testDip = new Setting<>("TestDip", false);
    private final Setting<Integer> dipInterval = new Setting<>("DipInterval", 3000, 500, 10000, v -> testDip.getValue());
    private final Setting<Boolean> noSpeedLoss = new Setting<>("NoSpeedLoss", false);
    private final Setting<Boolean> autoClimb = new Setting<>("AutoClimb", false);
    private final Setting<Boolean> burstMode = new Setting<>("BurstMode", false);
    private final Setting<Float> burstStrength = new Setting<>("BurstStrength", 1.5f, 0.5f, 3.0f, v -> burstMode.getValue());
    private final Setting<Boolean> glideBoost = new Setting<>("GlideBoost", false);
    private final Setting<Float> glideFactor = new Setting<>("GlideFactor", 1.2f, 0.5f, 2.0f, v -> glideBoost.getValue());
    private final Setting<Boolean> waveMode = new Setting<>("WaveMode", false);
    private final Setting<Float> waveAmplitude = new Setting<>("WaveAmplitude", 0.5f, 0.1f, 1.5f, v -> waveMode.getValue());
    private final Setting<Boolean> teleportMode = new Setting<>("TeleportMode", false);
    private final Setting<Integer> teleportInterval = new Setting<>("TeleportInterval", 20, 5, 100, v -> teleportMode.getValue());
    private final Setting<Boolean> entityMagnet = new Setting<>("EntityMagnet", false);
    private final Setting<Float> magnetRange = new Setting<>("MagnetRange", 5f, 1f, 15f, v -> entityMagnet.getValue());

    public enum Mode {Boost, Control, FireWork, Packet}
    public enum GrimBypass {Vanilla, Strict, Aggressive, Silent}

    private final thunder.hack.utility.Timer startTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer redeployTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer strictTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer pingTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer recastTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer dipTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer teleportTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer boostCooldownTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer jumpTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer syncTimer = new thunder.hack.utility.Timer();
    
    private boolean waitingForConfirm = false;
    private long lastBoostTime = 0;
    private boolean hasAutoRecasted = false;
    private int dipState = 0;
    private Vec3d lastConfirmedPos = null;
    private boolean hasJumpedForGlide = false;
    private boolean sentTeleport = false;
    private int syncTicks = 0;

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
        reset();
        infiniteFlag = false;
        acceleration = 0;
        accelerationY = 0;
        hasJumpedForGlide = false;
        syncTicks = 0;
        lastConfirmedPos = null;

        if (mc.player != null)
            height = (float) mc.player.getY();

        pingTimer.reset();
        recastTimer.reset();
        dipTimer.reset();
        teleportTimer.reset();
        dipState = 0;
        hasAutoRecasted = false;
    }

    @EventHandler
    public void modifyVelocity(EventTravel e) {
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
        if (mode.is(Mode.Boost)) {
            doPreLegacy();
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

            if (syncOnTeleport.getValue() && syncTicks > 0) {
                syncTicks--;
                if (syncTicks <= 0) {
                    waitingForConfirm = false;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerUpdate(PlayerUpdateEvent e) {
        if (mode.is(Mode.Boost) && autoRecast.getValue() && !hasAutoRecasted && mc.player.isFallFlying() && recastTimer.passedMs(recastDelay.getValue())) {
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            hasAutoRecasted = true;
            recastTimer.reset();
        }
        
        if (mode.is(Mode.Boost) && !mc.player.isFallFlying() && hasAutoRecasted) {
            hasAutoRecasted = false;
        }
        
        if (mode.is(Mode.Boost) && testDip.getValue() && mc.player.isFallFlying() && dipTimer.passedMs(dipInterval.getValue())) {
            if (dipState == 0) {
                Vec3d velocity = mc.player.getVelocity();
                mc.player.setVelocity(velocity.x, velocity.y - 0.5, velocity.z);
                dipState = 1;
            } else {
                dipState = 0;
            }
            dipTimer.reset();
        }
        
        if (mode.is(Mode.Boost) && teleportMode.getValue() && mc.player.isFallFlying() && teleportTimer.passedMs(teleportInterval.getValue())) {
            Vec3d lookVec = mc.player.getRotationVec(1.0f);
            Vec3d newPos = mc.player.getPos().add(lookVec.multiply(5.0));
            mc.player.setPosition(newPos.x, newPos.y, newPos.z);
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            teleportTimer.reset();
        }
        
        if (mode.is(Mode.Boost) && entityMagnet.getValue() && mc.player.isFallFlying()) {
            for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
                if (entity != mc.player && mc.player.distanceTo(entity) < magnetRange.getValue()) {
                    Vec3d vec = entity.getPos().subtract(mc.player.getPos()).normalize();
                    mc.player.setVelocity(mc.player.getVelocity().add(vec.multiply(0.1)));
                }
            }
        }
    }

    private void doPreLegacy() {
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

    private int getCurrentPing() {
        if (mc.getNetworkHandler() == null) return 0;
        var playerEntry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        if (playerEntry == null) return 0;
        return playerEntry.getLatency();
    }

    private void doBoost(EventMove e) {
        if (mc.player.getInventory().getStack(38).getItem() != Items.ELYTRA) {
            return;
        }
        
        // ===== ВЗЛЕТ С МЕСТА (с обходом ElytraB = требуется прыжок) =====
        if (mc.player.isOnGround() && mc.options.jumpKey.isPressed()) {
            if (jumpBeforeGlide.getValue()) {
                if (!hasJumpedForGlide && jumpTimer.passedMs(jumpDelay.getValue())) {
                    // Прыжок
                    mc.player.setVelocity(mc.player.getVelocity().x, 1.2, mc.player.getVelocity().z);
                    mc.player.jump();
                    hasJumpedForGlide = true;
                    jumpTimer.reset();
                } else if (hasJumpedForGlide && jumpTimer.passedMs(jumpDelay.getValue())) {
                    // Активация элитры после прыжка
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
        
        // ===== СИНХРОНИЗАЦИЯ ПОЗИЦИИ (анти-рубербенд) =====
        if (antiRubberband.getValue() && lastConfirmedPos != null && mc.player.getPos().distanceTo(lastConfirmedPos) > 2.0) {
            mc.player.setPosition(lastConfirmedPos.x, lastConfirmedPos.y, lastConfirmedPos.z);
        }
        
        // ===== ЗАДЕРЖКА МЕЖДУ БУСТАМИ (адаптивная) =====
        int currentPing = getCurrentPing();
        if (currentPing == 0) currentPing = 140;
        
        long minDelay;
        switch (grimBypass.getValue()) {
            case Aggressive:
                minDelay = Math.min(200, Math.max(40, (long)(currentPing * 0.4)));
                break;
            case Strict:
                minDelay = Math.min(350, Math.max(120, (long)(currentPing * 1.2)));
                break;
            case Silent:
                minDelay = Math.min(400, Math.max(150, (long)(currentPing * 1.5)));
                break;
            default:
                minDelay = Math.min(300, Math.max(80, (long)(currentPing * 0.8)));
        }
        
        if (!boostCooldownTimer.passedMs(minDelay)) {
            return;
        }
        
        // ===== FAKE JUMP (обход ElytraB) =====
        if (fakeJump.getValue() && mc.player.age % 20 == 0 && mc.player.isFallFlying()) {
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
        
        // ===== БУСТ =====
        if (twoBee.getValue()) {
            if ((mc.options.jumpKey.isPressed() || !onlySpace.getValue() || cruiseControl.getValue())) {
                double[] m = MovementUtility.forwardWithoutStrafe((factor.getValue() / 10f));
                e.setX(e.getX() + m[0]);
                e.setZ(e.getZ() + m[1]);
            }
        }
        
        // ===== ДОПОЛНИТЕЛЬНЫЕ РЕЖИМЫ =====
        if (noSpeedLoss.getValue()) {
            double currentSpeed = Math.hypot(e.getX(), e.getZ());
            if (currentSpeed < 0.1) {
                double[] dir = MovementUtility.forward(0.5);
                e.setX(e.getX() + dir[0]);
                e.setZ(e.getZ() + dir[1]);
            }
        }
        
        if (autoClimb.getValue() && mc.options.jumpKey.isPressed()) {
            e.setY(e.getY() + 0.3);
        }
        
        if (burstMode.getValue() && mc.player.age % 10 == 0) {
            double[] dir = MovementUtility.forward(burstStrength.getValue() / 5f);
            e.setX(e.getX() + dir[0]);
            e.setZ(e.getZ() + dir[1]);
        }
        
        if (glideBoost.getValue()) {
            e.setY(e.getY() - 0.05);
            double[] dir = MovementUtility.forward(glideFactor.getValue() / 8f);
            e.setX(e.getX() + dir[0]);
            e.setZ(e.getZ() + dir[1]);
        }
        
        if (waveMode.getValue()) {
            double wave = Math.sin(mc.player.age * 0.1) * waveAmplitude.getValue();
            e.setY(e.getY() + wave * 0.1);
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
        
        if (syncOnTeleport.getValue()) {
            syncTicks = syncDelay.getValue();
        }
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

    // ===== ЗАГЛУШКИ ДЛЯ НЕИСПОЛЬЗУЕМЫХ МЕТОДОВ (оригинальные методы из ThunderHack) =====
    private final Setting<Float> xzSpeed = new Setting<>("XZSpeed", 1.55f, 0.1f, 10f, v -> mode.is(Mode.Control));
    private final Setting<Float> ySpeed = new Setting<>("YSpeed", 0.47f, 0f, 2f, v -> mode.is(Mode.Control));
    private final Setting<Float> upSpeed = new Setting<>("UpSpeed", 1.0f, 0.01f, 5.0f, v -> mode.is(Mode.Control));
    private final Setting<Float> downFactor = new Setting<>("Glide", 1.0f, 0.0f, 2.0f, v -> mode.is(Mode.Control));
    private final Setting<Float> sneakDownSpeed = new Setting<>("DownSpeed", 1.0f, 0.01f, 5.0f, v -> mode.is(Mode.Control));
    private final Setting<Boolean> accelerate = new Setting<>("Acceleration", false, v -> mode.is(Mode.Control));
    private final Setting<Float> accelerateFactor = new Setting<>("AccelerateFactor", 9f, 0f, 100f, v -> mode.is(Mode.Control));
    private final Setting<Boolean> instantFly = new Setting<>("InstantFly", true, v -> mode.is(Mode.Boost));
    private final Setting<Boolean> cruiseControl = new Setting<>("CruiseControl", false, v -> mode.is(Mode.Boost));
    private final Setting<Float> redeployDelay = new Setting<>("RedeployDelay", 0.5F, 0.1F, 1F);
    private final Setting<Integer> redeployInterval = new Setting<>("RedeployInterval", 1F, 0.1F, 5F);
    private final Setting<Integer> redeployTimeOut = new Setting<>("RedeployTimeout", 5f, 0.1f, 20f);
    private final Setting<Boolean> stopMotion = new Setting<>("StopMotion", true);
    private final Setting<Float> minUpSpeed = new Setting<>("MinUpSpeed", 0.5f, 0.1f, 5.0f);
    private final Setting<Boolean> forceHeight = new Setting<>("ForceHeight", false);
    private final Setting<Integer> manualHeight = new Setting<>("Height", 121, 1, 256);

    private final thunder.hack.utility.Timer timer = new thunder.hack.utility.Timer();
    private float prevClientPitch;
    private long lastFireworkTime;
    private int slotWithFireWorks = -1;
    private Item prevItemInHand = Items.AIR;
    private ItemStack getStackInSlotCopy;
    private int prevElytraSlot = -1;
    private Item prevArmorItem = Items.AIR;
    private ItemStack prevArmorItemCopy;
    private Vec3d flightZonePos;
    private boolean flying;
    private boolean started;
    private int disablerTicks;
    private boolean elytraEquiped;
    private float acceleration;
    private float accelerationY;
    private float height;
    private long lastFireworkTimeVar;
    private int slotWithFireWorksVar = -1;
}
