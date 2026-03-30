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

    public final Setting<Mode> mode = new Setting<>("Mode", Mode.FireWork);
    private final Setting<Integer> disablerDelay = new Setting<>("DisablerDelay", 1, 0, 10, v -> mode.is(Mode.SunriseOld));
    private final Setting<Boolean> twoBee = new Setting<>("2b2t", false, v -> mode.is(Mode.Boost));
    private final Setting<Boolean> onlySpace = new Setting<>("OnlySpace", true, v -> mode.is(Mode.Boost) && twoBee.getValue());
    private final Setting<Boolean> stopOnGround = new Setting<>("StopOnGround", false, v -> mode.is(Mode.Packet));
    private final Setting<Boolean> infDurability = new Setting<>("InfDurability", true, v -> mode.is(Mode.Packet));
    private final Setting<Boolean> vertical = new Setting<>("Vertical", false, v -> mode.is(Mode.Packet));
    private final Setting<NCPStrict> ncpStrict = new Setting<>("NCPStrict", NCPStrict.Off, v -> mode.is(Mode.Packet));
    private final Setting<AntiKick> antiKick = new Setting<>("AntiKick", AntiKick.Jitter, v -> mode.is(Mode.FireWork) || mode.is(Mode.SunriseOld));
    private final Setting<Float> xzSpeed = new Setting<>("XZSpeed", 1.55f, 0.1f, 10f, v -> !mode.is(Mode.Boost) && mode.getValue() != Mode.Pitch40Infinite);
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
    private final Setting<Float> factor = new Setting<>("Factor", 1.5f, 0.1f, 50.0f, v -> mode.is(Mode.Boost));
    private final Setting<Float> upSpeed = new Setting<>("UpSpeed", 1.0f, 0.01f, 5.0f, v -> ((mode.is(Mode.Boost) && !twoBee.getValue()) || mode.is(Mode.Control)));
    private final Setting<Float> downFactor = new Setting<>("Glide", 1.0f, 0.0f, 2.0f, v -> ((mode.is(Mode.Boost) && !twoBee.getValue()) || mode.is(Mode.Control)));
    private final Setting<Boolean> stopMotion = new Setting<>("StopMotion", true, v -> mode.is(Mode.Boost) && !twoBee.getValue());
    private final Setting<Float> minUpSpeed = new Setting<>("MinUpSpeed", 0.5f, 0.1f, 5.0f, v -> mode.is(Mode.Boost) && cruiseControl.getValue());
    private final Setting<Boolean> forceHeight = new Setting<>("ForceHeight", false, v -> (mode.is(Mode.Boost) && cruiseControl.getValue()));
    private final Setting<Integer> manualHeight = new Setting<>("Height", 121, 1, 256, v -> mode.is(Mode.Boost) && forceHeight.getValue());
    private final Setting<Float> sneakDownSpeed = new Setting<>("DownSpeed", 1.0f, 0.01f, 5.0f, v -> mode.is(Mode.Control));
    private final Setting<Boolean> speedLimit = new Setting<>("SpeedLimit", true, v -> mode.is(Mode.Boost));
    private final Setting<Float> maxSpeed = new Setting<>("MaxSpeed", 2.5f, 0.1f, 10.0f, v -> mode.is(Mode.Boost));
    private final Setting<Float> redeployInterval = new Setting<>("RedeployInterval", 1F, 0.1F, 5F, v -> mode.is(Mode.Boost) && !twoBee.getValue());
    private final Setting<Float> redeployTimeOut = new Setting<>("RedeployTimeout", 5f, 0.1f, 20f, v -> mode.is(Mode.Boost) && !twoBee.getValue());
    private final Setting<Float> redeployDelay = new Setting<>("RedeployDelay", 0.5F, 0.1F, 1F, v -> mode.is(Mode.Boost) && !twoBee.getValue());
    private final Setting<Float> infiniteMaxSpeed = new Setting<>("InfiniteMaxSpeed", 150f, 50f, 170f, v -> mode.getValue() == Mode.Pitch40Infinite);
    private final Setting<Float> infiniteMinSpeed = new Setting<>("InfiniteMinSpeed", 25f, 10f, 70f, v -> mode.getValue() == Mode.Pitch40Infinite);
    private final Setting<Integer> infiniteMaxHeight = new Setting<>("InfiniteMaxHeight", 200, 50, 360, v -> mode.getValue() == Mode.Pitch40Infinite);
    
    private final Setting<Boolean> autoRecast = new Setting<>("AutoRecast", false);
    private final Setting<Integer> recastDelay = new Setting<>("RecastDelay", 1000, 100, 5000, v -> autoRecast.getValue());
    private final Setting<Boolean> testDip = new Setting<>("TestDip", false);
    private final Setting<Integer> dipInterval = new Setting<>("DipInterval", 3000, 500, 10000, v -> testDip.getValue());
    private final Setting<Boolean> noSpeedLoss = new Setting<>("NoSpeedLoss", false);
    private final Setting<Boolean> autoClimb = new Setting<>("AutoClimb", false);
    private final Setting<Boolean> noFallRequired = new Setting<>("NoFallRequired", false);
    private final Setting<Boolean> burstMode = new Setting<>("BurstMode", false);
    private final Setting<Float> burstStrength = new Setting<>("BurstStrength", 1.5f, 0.5f, 3.0f, v -> burstMode.getValue());
    private final Setting<Boolean> glideBoost = new Setting<>("GlideBoost", false);
    private final Setting<Float> glideFactor = new Setting<>("GlideFactor", 1.2f, 0.5f, 2.0f, v -> glideBoost.getValue());
    private final Setting<Boolean> orbitMode = new Setting<>("OrbitMode", false);
    private final Setting<Float> orbitRadius = new Setting<>("OrbitRadius", 3.0f, 1.0f, 8.0f, v -> orbitMode.getValue());
    private final Setting<Boolean> waveMode = new Setting<>("WaveMode", false);
    private final Setting<Float> waveAmplitude = new Setting<>("WaveAmplitude", 0.5f, 0.1f, 1.5f, v -> waveMode.getValue());
    private final Setting<Boolean> teleportMode = new Setting<>("TeleportMode", false);
    private final Setting<Integer> teleportInterval = new Setting<>("TeleportInterval", 20, 5, 100, v -> teleportMode.getValue());
    private final Setting<Boolean> antiRubberband = new Setting<>("AntiRubberband", true);
    private final Setting<Boolean> positionSync = new Setting<>("PositionSync", true);
    private final Setting<Boolean> packetFlood = new Setting<>("PacketFlood", false);
    private final Setting<Integer> floodRate = new Setting<>("FloodRate", 5, 1, 20, v -> packetFlood.getValue());
    private final Setting<Boolean> grimBypass = new Setting<>("GrimBypass", true);
    private final Setting<Boolean> matrixBypass = new Setting<>("MatrixBypass", false);
    private final Setting<Boolean> fakeLag = new Setting<>("FakeLag", false);
    private final Setting<Integer> lagTicks = new Setting<>("LagTicks", 10, 1, 40, v -> fakeLag.getValue());
    private final Setting<Boolean> spinMode = new Setting<>("SpinMode", false);
    private final Setting<Float> spinSpeed = new Setting<>("SpinSpeed", 180f, 10f, 360f, v -> spinMode.getValue());
    private final Setting<Boolean> teleportBack = new Setting<>("TeleportBack", false);
    private final Setting<Float> teleportBackDistance = new Setting<>("TeleportBackDistance", 3f, 1f, 10f, v -> teleportBack.getValue());
    private final Setting<Boolean> entityMagnet = new Setting<>("EntityMagnet", false);
    private final Setting<Float> magnetRange = new Setting<>("MagnetRange", 5f, 1f, 15f, v -> entityMagnet.getValue());

    public enum Mode {FireWork, SunriseOld, Boost, Control, Pitch40Infinite, SunriseNew, Packet}

    public enum AntiKick {Off, Jitter, Glide}

    public enum NCPStrict {Off, Old, New, Motion}

    private final thunder.hack.utility.Timer startTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer redeployTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer strictTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer pingTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer recastTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer dipTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer orbitTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer teleportTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer floodTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer lagTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer boostCooldownTimer = new thunder.hack.utility.Timer();
    private boolean waitingForConfirm = false;
    private long lastBoostTime = 0;
    private boolean hasAutoRecasted = false;
    private int dipState = 0;
    private double orbitAngle = 0;
    private Vec3d originalPos = null;
    private Vec3d lastConfirmedPos = null;
    private int floodCount = 0;
    private int lagTicksLeft = 0;

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
        if (mc.player.getY() < infiniteMaxHeight.getValue() && mode.getValue() == Mode.Pitch40Infinite) {
            disable(
                    isRu() ?
                            "Поднимись выше " + Formatting.AQUA + infiniteMaxHeight.getValue() + Formatting.GRAY + " высоты!" :
                            "Go above " + Formatting.AQUA + infiniteMaxHeight.getValue() + Formatting.GRAY + " height!"
            );
        }

        flying = false;
        reset();

        infiniteFlag = false;
        acceleration = 0;
        accelerationY = 0;

        if (mc.player != null)
            height = (float) mc.player.getY();

        pingTimer.reset();
        recastTimer.reset();
        dipTimer.reset();
        orbitTimer.reset();
        teleportTimer.reset();
        floodTimer.reset();
        lagTimer.reset();
        dipState = 0;
        orbitAngle = 0;
        hasAutoRecasted = false;
        floodCount = 0;
        lagTicksLeft = 0;
        lastConfirmedPos = null;

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

    private void doPacket(EventSync e) {
        if ((!isBoxCollidingGround() || !stopOnGround.getValue()) && mc.player.getInventory().getStack(38).getItem() == Items.ELYTRA) {
            if (infDurability.getValue() || !mc.player.isFallFlying())
                sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));

            if (mc.player.age % 3 != 0 && ncpStrict.is(NCPStrict.Motion))
                e.cancel();
        }
    }

    private void doPitch40Infinite() {
        ItemStack is = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (is.isOf(Items.ELYTRA)) {
            mc.player.setPitch(lastInfinitePitch);
            if (is.getDamage() > 380 && mc.player.age % 100 == 0) {
                Managers.NOTIFICATION.publicity("Elytra+", isRu() ? "Элитра скоро сломается!" : "Elytra's about to break!", 2, Notification.Type.WARNING);
                mc.world.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.AMBIENT, 10.0f, 1.0F, 0);
            }
        }
    }

    private void doSunriseNew() {
        if (mc.player.horizontalCollision)
            acceleration = 0;

        int elytra = InventoryUtility.getElytra();

        if (elytra == -1)
            return;

        if (mc.player.isOnGround()) {
            mc.player.jump();
            acceleration = 0;
            return;
        }

        if (mc.player.fallDistance <= 0)
            return;

        if (mc.options.jumpKey.isPressed() || mc.options.sneakKey.isPressed()) {
            acceleration = 0;
            takeOnElytra();
        } else {
            takeOnChestPlate();
            if (mc.player.age % 8 == 0)
                matrixDisabler(elytra);

            MovementUtility.setMotion(Math.min((acceleration = (acceleration + 8.0F / xzSpeed.getValue())) / 100.0F, xzSpeed.getValue()));
            if (!MovementUtility.isMoving()) acceleration = 0;
            mc.player.setVelocity(mc.player.getVelocity().getX(), -0.005F, mc.player.getVelocity().getZ());
        }
    }

    private void takeOnElytra() {
        int elytra = InventoryUtility.getElytra();
        if (elytra == -1) return;
        elytra = elytra >= 0 && elytra < 9 ? elytra + 36 : elytra;
        if (elytra != -2) {
            clickSlot(elytra);
            clickSlot(6);
            clickSlot(elytra);
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
    }

    private void takeOnChestPlate() {
        int slot = getChestPlateSlot();
        if (slot == -1) return;
        if (slot != -2) {
            clickSlot(slot);
            clickSlot(6);
            clickSlot(slot);
        }
    }

    private float getInfinitePitch() {
        if (mc.player.getY() < infiniteMaxHeight.getValue()) {
            if (Managers.PLAYER.currentPlayerSpeed * 72f < infiniteMinSpeed.getValue() && !infiniteFlag)
                infiniteFlag = true;
            if (Managers.PLAYER.currentPlayerSpeed * 72f > infiniteMaxSpeed.getValue() && infiniteFlag)
                infiniteFlag = false;
        } else infiniteFlag = true;

        if (infiniteFlag) infinitePitch += 3;
        else infinitePitch -= 3;

        infinitePitch = MathUtility.clamp(infinitePitch, -40, 40);
        return infinitePitch;
    }

    @Override
    public void onDisable() {
        ThunderHack.TICK_TIMER = 1.0f;
        mc.player.getAbilities().flying = false;
        mc.player.getAbilities().setFlySpeed(0.05F);
        if (mode.is(Mode.FireWork))
            fireworkOnDisable();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(EventMove e) {
        switch (mode.getValue()) {
            case Boost -> doBoost(e);
            case Control -> doControl(e);
            case FireWork -> fireworkOnMove(e);
            case Packet -> doMotionPacket(e);
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.SendPost event) {
        if (fullNullCheck()) return;

        if (event.getPacket() instanceof ClientCommandC2SPacket command && mode.is(Mode.FireWork))
            if (command.getMode() == ClientCommandC2SPacket.Mode.START_FALL_FLYING)
                doFireWork(false);

        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket p && mode.is(Mode.FireWork) && grim.getValue().isEnabled() && fireWorkExtender.getValue())
            if (flying && flightZonePos != null && Criticals.getEntity(p).age < (pingTimer.getPassedTimeMs() / 50f))
                sendMessage(Formatting.RED + (isRu() ? "В этом режиме нельзя бить сущностей которые появились после включения модуля!" : "In this mode, you cannot hit entities that spawned after the module was turned on!"));
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (e.getPacket() instanceof EntityTrackerUpdateS2CPacket pac && pac.id() == mc.player.getId() && (mode.is(Mode.Packet) || mode.is(Mode.SunriseOld))) {
            List<DataTracker.SerializedEntry<?>> values = pac.trackedValues();
            if (values.isEmpty())
                return;

            for (DataTracker.SerializedEntry<?> value : values)
                if (value.value().toString().equals("FALL_FLYING") || (value.id() == 0 && (value.value().toString().equals("-120") || value.value().toString().equals("-128") || value.value().toString().equals("-126"))))
                    e.cancel();
        }

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

            if (disableOnFlag.getValue() && mode.is(Mode.FireWork))
                disable(isRu() ? "Выключен из-за флага!" : "Disabled due to flag!");
        }

        if (e.getPacket() instanceof CommonPingS2CPacket && mode.is(Mode.FireWork) && grim.getValue().isEnabled() && fireWorkExtender.getValue() && flying)
            if (!pingTimer.passedMs(50000)) {
                if (pingTimer.passedMs(1000) && PlayerUtility.getSquaredDistance2D(flightZonePos) < 7000)
                    e.cancel();
            } else pingTimer.reset();
    }

    @EventHandler
    public void onPlayerUpdate(PlayerUpdateEvent e) {
        switch (mode.getValue()) {
            case FireWork -> fireWorkOnPlayerUpdate();
            case Pitch40Infinite -> lastInfinitePitch = PlayerUtility.fixAngle(getInfinitePitch());
        }
        
        if (fakeLag.getValue()) {
            if (lagTicksLeft > 0) {
                lagTicksLeft--;
                return;
            }
            if (lagTimer.passedMs(5000)) {
                lagTicksLeft = lagTicks.getValue();
                lagTimer.reset();
            }
        }
        
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
        
        if (mode.is(Mode.Boost) && orbitMode.getValue() && mc.player.isFallFlying()) {
            orbitAngle += 0.1;
            if (orbitAngle > Math.PI * 2) orbitAngle -= Math.PI * 2;
            double x = Math.cos(orbitAngle) * orbitRadius.getValue();
            double z = Math.sin(orbitAngle) * orbitRadius.getValue();
            Vec3d targetPos = originalPos != null ? originalPos.add(x, 0, z) : mc.player.getPos().add(x, 0, z);
            mc.player.setPosition(targetPos.x, mc.player.getY(), targetPos.z);
            if (originalPos == null) originalPos = mc.player.getPos();
        } else {
            originalPos = null;
        }
        
        if (mode.is(Mode.Boost) && teleportMode.getValue() && mc.player.isFallFlying() && teleportTimer.passedMs(teleportInterval.getValue())) {
            Vec3d lookVec = mc.player.getRotationVec(1.0f);
            Vec3d newPos = mc.player.getPos().add(lookVec.multiply(5.0));
            mc.player.setPosition(newPos.x, newPos.y, newPos.z);
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            teleportTimer.reset();
        }
        
        if (mode.is(Mode.Boost) && teleportBack.getValue() && lastConfirmedPos != null && mc.player.getPos().distanceTo(lastConfirmedPos) > teleportBackDistance.getValue()) {
            mc.player.setPosition(lastConfirmedPos.x, lastConfirmedPos.y, lastConfirmedPos.z);
        }
        
        if (mode.is(Mode.Boost) && spinMode.getValue() && mc.player.isFallFlying()) {
            mc.player.setYaw(mc.player.getYaw() + spinSpeed.getValue() / 20f);
        }
        
        if (mode.is(Mode.Boost) && entityMagnet.getValue() && mc.player.isFallFlying()) {
            for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
                if (entity != mc.player && mc.player.distanceTo(entity) < magnetRange.getValue()) {
                    Vec3d vec = entity.getPos().subtract(mc.player.getPos()).normalize();
                    mc.player.setVelocity(mc.player.getVelocity().add(vec.multiply(0.1)));
                }
            }
        }
        
        if (mode.is(Mode.Boost) && packetFlood.getValue() && floodTimer.passedMs(1000 / floodRate.getValue())) {
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            floodTimer.reset();
        }
    }

    private void doMotionPacket(EventMove e) {
        mc.player.getAbilities().flying = false;
        mc.player.getAbilities().setFlySpeed(0.05F);

        if ((isBoxCollidingGround() && stopOnGround.getValue()) || mc.player.getInventory().getStack(38).getItem() != Items.ELYTRA)
            return;

        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().setFlySpeed((xzSpeed.getValue() / 15f) * (accelerate.getValue().isEnabled() ? Math.min((acceleration += accelerateFactor.getValue()) / 100.0f, 1.0f) : 1f));
        e.cancel();

        if (mc.player.age % 3 == 0 && ncpStrict.is(NCPStrict.Motion)) {
            e.setY(0);
            e.setX(0);
            e.setZ(0);
            return;
        }

        if (Math.abs(e.getX()) < 0.05)
            e.setX(0);

        if (Math.abs(e.getZ()) < 0.05)
            e.setZ(0);

        e.setY(vertical.getValue() ? mc.options.jumpKey.isPressed() ? ySpeed.getValue() : mc.options.sneakKey.isPressed() ? -ySpeed.getValue() : 0 : 0);

        switch (ncpStrict.getValue()) {
            case New -> e.setY(-1.000088900582341E-12);
            case Motion -> e.setY(-4.000355602329364E-12);
            case Old -> e.setY(0.0002 - (mc.player.age % 2 == 0 ? 0 : 0.000001) + MathUtility.random(0, 0.0000009));
        }

        if (mc.player.horizontalCollision && (ncpStrict.is(NCPStrict.New) || ncpStrict.is(NCPStrict.Motion)) && mc.player.age % 2 == 0)
            e.setY(-0.07840000152587923);

        if ((infDurability.getValue() || ncpStrict.is(NCPStrict.Motion))) {
            if (!MovementUtility.isMoving() && Math.abs(e.getX()) < 0.121) {
                float angleToRad = (float) Math.toRadians(4.5 * (mc.player.age % 80));
                e.setX(Math.sin(angleToRad) * 0.12);
                e.setZ(Math.cos(angleToRad) * 0.12);
            }
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

    public void onRender3D(MatrixStack stack) {
        if (mode.is(Mode.FireWork) && grim.getValue().isEnabled() && fireWorkExtender.getValue() && flying && flightZonePos != null) {
            stack.push();
            Render3DEngine.setupRender();
            RenderSystem.disableCull();
            Tessellator tessellator = Tessellator.getInstance();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

            float cos;
            float sin;
            for (int i = 0; i <= 30; i++) {
                cos = (float) ((flightZonePos.getX() - mc.getEntityRenderDispatcher().camera.getPos().getX()) + Math.cos(i * (Math.PI * 2f) / 30f) * 95);
                sin = (float) ((flightZonePos.getZ() - mc.getEntityRenderDispatcher().camera.getPos().getZ()) + Math.sin(i * (Math.PI * 2f) / 30f) * 95);
                bufferBuilder.vertex(stack.peek().getPositionMatrix(), cos, (float) -mc.getEntityRenderDispatcher().camera.getPos().getY(), sin).color(Render2DEngine.injectAlpha(HudEditor.getColor(i), 255).getRGB());
                bufferBuilder.vertex(stack.peek().getPositionMatrix(), cos, (float) ((float) 128 - mc.getEntityRenderDispatcher().camera.getPos().getY()), sin).color(Render2DEngine.injectAlpha(HudEditor.getColor(i), 0).getRGB());
            }
            Render2DEngine.endBuilding(bufferBuilder);
            RenderSystem.enableCull();
            Render3DEngine.endRender();
            stack.pop();
        }
    }

    public void onRender2D(DrawContext context) {
        if (mode.is(Mode.FireWork) && grim.getValue().isEnabled() && fireWorkExtender.getValue() && flying) {
            if (!pingTimer.passed
