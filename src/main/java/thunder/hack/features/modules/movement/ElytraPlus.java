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
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.*;
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
    private final Setting<Boolean> instantFly = new Setting<>("InstantFly", true, v -> mode.is(Mode.Control));
    private final Setting<Boolean> cruiseControl = new Setting<>("CruiseControl", false, v -> mode.is(Mode.Boost));
    private final Setting<Float> factor = new Setting<>("Factor", 0.09f, 0.01f, 1.0f, v -> mode.is(Mode.Boost));
    private final Setting<Float> upSpeed = new Setting<>("UpSpeed", 1.0f, 0.01f, 5.0f, v -> mode.is(Mode.Control));
    private final Setting<Float> downFactor = new Setting<>("Glide", 1.0f, 0.0f, 2.0f, v -> mode.is(Mode.Control));
    private final Setting<Boolean> stopMotion = new Setting<>("StopMotion", true, v -> mode.is(Mode.Boost));
    private final Setting<Float> minUpSpeed = new Setting<>("MinUpSpeed", 0.5f, 0.1f, 5.0f, v -> mode.is(Mode.Boost) && cruiseControl.getValue());
    private final Setting<Boolean> forceHeight = new Setting<>("ForceHeight", false, v -> mode.is(Mode.Boost) && cruiseControl.getValue());
    private final Setting<Integer> manualHeight = new Setting<>("Height", 121, 1, 256, v -> mode.is(Mode.Boost) && forceHeight.getValue());
    private final Setting<Float> sneakDownSpeed = new Setting<>("DownSpeed", 1.0f, 0.01f, 5.0f, v -> mode.is(Mode.Control));
    private final Setting<Boolean> speedLimit = new Setting<>("SpeedLimit", true, v -> mode.is(Mode.Boost));
    private final Setting<Float> maxSpeed = new Setting<>("MaxSpeed", 2.5f, 0.1f, 10.0f, v -> mode.is(Mode.Boost));

    public enum Mode { FireWork, SunriseOld, Boost, Control, Pitch40Infinite, SunriseNew, Packet }
    public enum AntiKick { Off, Jitter, Glide }
    public enum NCPStrict { Off, Old, New, Motion }

    private final thunder.hack.utility.Timer startTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer redeployTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer strictTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer pingTimer = new thunder.hack.utility.Timer();

    private boolean infiniteFlag, hasTouchedGround, elytraEquiped, flying, started;
    private float acceleration, accelerationY, height, prevClientPitch, infinitePitch, lastInfinitePitch;
    private ItemStack prevArmorItemCopy, getStackInSlotCopy;
    private Item prevArmorItem = Items.AIR, prevItemInHand = Items.AIR;
    private Vec3d flightZonePos;
    private int prevElytraSlot = -1, disablerTicks, slotWithFireWorks = -1;
    private long lastFireworkTime;

    @Override public void onEnable() {
        if (mc.player.getY() < 200 && mode.is(Mode.Pitch40Infinite)) disable("Go above 200 height!");
        flying = false; reset(); infiniteFlag = false; acceleration = 0; accelerationY = 0;
        if (mode.is(Mode.FireWork)) fireworkOnEnable();
    }

    @Override public void onDisable() {
        ThunderHack.TICK_TIMER = 1.0f;
        mc.player.getAbilities().flying = false;
        mc.player.getAbilities().setFlySpeed(0.05F);
        if (mode.is(Mode.FireWork)) fireworkOnDisable();
    }

    @EventHandler public void modifyVelocity(EventTravel e) {
        if (mode.is(Mode.Pitch40Infinite)) {
            if (e.isPre()) { prevClientPitch = mc.player.getPitch(); mc.player.setPitch(lastInfinitePitch); }
            else mc.player.setPitch(prevClientPitch);
        }
        if (mode.is(Mode.FireWork) && Managers.PLAYER.ticksElytraFlying < 4) {
            if (e.isPre()) { prevClientPitch = mc.player.getPitch(); mc.player.setPitch(-45f); }
            else mc.player.setPitch(prevClientPitch);
        }
    }

    @EventHandler public void onSync(EventSync e) {
        switch (mode.getValue()) {
            case SunriseOld -> doSunrise();
            case FireWork -> fireworkOnSync();
            case Pitch40Infinite -> doPitch40Infinite();
            case Packet -> doPacket(e);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(EventMove e) {
        switch (mode.getValue()) {
            case Boost -> doGrimBoost(e);
            case Control -> doControl(e);
            case FireWork -> fireworkOnMove(e);
            case Packet -> doMotionPacket(e);
        }
    }

    private void doGrimBoost(EventMove e) {
        if (mc.player.getInventory().getStack(38).getItem() != Items.ELYTRA || !mc.player.isFallFlying() || mc.player.isTouchingWater() || mc.player.isInLava())
            return;

        Vec3d movement = new Vec3d(e.getX(), e.getY(), e.getZ());
        double yaw = Math.toRadians(mc.player.getYaw() + 90.0);
        double dx = factor.getValue() * Math.cos(yaw);
        double dz = factor.getValue() * Math.sin(yaw);
        movement = movement.add(dx, 0.0, dz);

        if (speedLimit.getValue()) {
            double speed = Math.hypot(movement.x, movement.z);
            if (speed > maxSpeed.getValue()) {
                movement = movement.multiply(maxSpeed.getValue() / speed);
            }
        }

        e.setX(movement.x);
        e.setY(movement.y);
        e.setZ(movement.z);
        mc.player.setVelocity(e.getX(), e.getY(), e.getZ());
        e.cancel();
    }

    private void doControl(EventMove e) {
        if (mc.player.getInventory().getStack(38).getItem() != Items.ELYTRA || !mc.player.isFallFlying()) return;
        double[] dir = MovementUtility.forward(xzSpeed.getValue() * (accelerate.getValue().isEnabled() ? Math.min((acceleration += accelerateFactor.getValue()) / 100.0f, 1.0f) : 1f));
        e.setX(dir[0]);
        e.setY(mc.options.jumpKey.isPressed() ? upSpeed.getValue() : mc.options.sneakKey.isPressed() ? -sneakDownSpeed.getValue() : -0.08 * downFactor.getValue());
        e.setZ(dir[1]);
        if (!MovementUtility.isMoving()) acceleration = 0;
        mc.player.setVelocity(e.getX(), e.getY(), e.getZ());
        e.cancel();
    }

    private void doSunrise() {
        if (mc.player.horizontalCollision) acceleration = 0;
        if (mc.player.verticalCollision) { acceleration = 0; mc.player.setVelocity(mc.player.getVelocity().getX(), 0.41999998688697815, mc.player.getVelocity().getZ()); }
        int elytra = InventoryUtility.getElytra();
        if (elytra == -1) return;
        if (mc.player.isOnGround()) mc.player.jump();
        if (disablerTicks-- <= 0) matrixDisabler(elytra);
        if (mc.player.fallDistance > 0.25f) {
            MovementUtility.setMotion(Math.min((acceleration = (acceleration + 11.0F / xzSpeed.getValue())) / 100.0F, xzSpeed.getValue()));
            if (!MovementUtility.isMoving()) acceleration = 0;
            if (InputUtil.isKeyPressed(mc.getWindow().getHandle(), bombKey.getValue().getKey())) {
                MovementUtility.setMotion(0.8f);
                mc.player.setVelocity(mc.player.getVelocity().getX(), mc.player.age % 2 == 0 ? 0.41999998688697815 : -0.41999998688697815, mc.player.getVelocity().getZ());
                acceleration = 70;
            } else {
                switch (antiKick.getValue()) {
                    case Jitter -> mc.player.setVelocity(mc.player.getVelocity().getX(), mc.player.age % 2 == 0 ? 0.08 : -0.08, mc.player.getVelocity().getZ());
                    case Glide -> mc.player.setVelocity(mc.player.getVelocity().getX(), -0.01F - (mc.player.age % 2 == 0 ? 1.0E-4F : 0.006F), mc.player.getVelocity().getZ());
                    case Off -> mc.player.setVelocity(mc.player.getVelocity().getX(), 0, mc.player.getVelocity().getZ());
                }
            }
            if (!mc.player.isSneaking() && mc.options.jumpKey.isPressed()) mc.player.setVelocity(mc.player.getVelocity().getX(), ySpeed.getValue(), mc.player.getVelocity().getZ());
            if (mc.options.sneakKey.isPressed()) mc.player.setVelocity(mc.player.getVelocity().getX(), -ySpeed.getValue(), mc.player.getVelocity().getZ());
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

    private void doPacket(EventSync e) {
        if ((!isBoxCollidingGround() || !stopOnGround.getValue()) && mc.player.getInventory().getStack(38).getItem() == Items.ELYTRA) {
            if (infDurability.getValue() || !mc.player.isFallFlying()) sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            if (mc.player.age % 3 != 0 && ncpStrict.is(NCPStrict.Motion)) e.cancel();
        }
    }

    private void doMotionPacket(EventMove e) {
        mc.player.getAbilities().flying = false;
        mc.player.getAbilities().setFlySpeed(0.05F);
        if ((isBoxCollidingGround() && stopOnGround.getValue()) || mc.player.getInventory().getStack(38).getItem() != Items.ELYTRA) return;
        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().setFlySpeed((xzSpeed.getValue() / 15f) * (accelerate.getValue().isEnabled() ? Math.min((acceleration += accelerateFactor.getValue()) / 100.0f, 1.0f) : 1f));
        e.cancel();
        if (mc.player.age % 3 == 0 && ncpStrict.is(NCPStrict.Motion)) { e.setY(0); e.setX(0); e.setZ(0); return; }
        if (Math.abs(e.getX()) < 0.05) e.setX(0);
        if (Math.abs(e.getZ()) < 0.05) e.setZ(0);
        e.setY(vertical.getValue() ? mc.options.jumpKey.isPressed() ? ySpeed.getValue() : mc.options.sneakKey.isPressed() ? -ySpeed.getValue() : 0 : 0);
    }

    private boolean isBoxCollidingGround() {
        return mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-0.25, 0.0, -0.25).offset(0.0, -0.3, 0.0)).iterator().hasNext();
    }

    public void matrixDisabler(int elytra) {
        elytra = elytra >= 0 && elytra < 9 ? elytra + 36 : elytra;
        if (elytra != -2) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, elytra, 1, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, 1, SlotActionType.PICKUP, mc.player);
        }
        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        if (elytra != -2) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, 1, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, elytra, 1, SlotActionType.PICKUP, mc.player);
        }
        disablerTicks = disablerDelay.getValue();
    }

    // ------------------ FIREWORK MODE ------------------
    private void fireworkOnEnable() {
        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA && mc.player.currentScreenHandler.getCursorStack().getItem() != Items.ELYTRA && InventoryUtility.getElytra() == -1) { noElytra(); return; }
        if (getFireWorks(false) == -1) { noFireworks(); return; }
        if (getFireWorks(true) != -1) return;
        getStackInSlotCopy = mc.player.getInventory().getStack(fireSlot.getValue() - 1).copy();
        prevItemInHand = mc.player.getInventory().getStack(fireSlot.getValue() - 1).getItem();
    }

    private void fireworkOnDisable() {
        started = false;
        if (keepFlying.getValue()) return;
        mc.player.setVelocity(0, mc.player.getVelocity().getY(), 0);
        new Thread(() -> {
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            ThunderHack.TICK_TIMER = 0.1f;
            returnItem(); reset();
            try { Thread.sleep(200L); } catch (InterruptedException e) { ThunderHack.TICK_TIMER = 1f; }
            returnChestPlate(); resetPrevItems();
            ThunderHack.TICK_TIMER = 1f;
        }).start();
    }

    private void fireworkOnSync() {
        if (grim.getValue().isEnabled() && rotate.getValue()) {
            if (mc.options.jumpKey.isPressed() && mc.player.isFallFlying() && flying) mc.player.setPitch(-45f);
            if (mc.options.sneakKey.isPressed() && mc.player.isFallFlying() && flying) mc.player.setPitch(45f);
            mc.player.setYaw(MovementUtility.getMoveDirection());
        }
        if (!MovementUtility.isMoving() && mc.options.jumpKey.isPressed() && mc.player.isFallFlying() && flying) mc.player.setPitch(-90f);
        if (Managers.PLAYER.ticksElytraFlying < 5 && !mc.player.isOnGround()) mc.player.setPitch(-45f);
    }

    private void fireworkOnMove(EventMove e) {
        if (mc.player.isFallFlying() && flying) {
            if (mc.player.horizontalCollision || mc.player.verticalCollision) { acceleration = 0; accelerationY = 0; }
            if (Managers.PLAYER.ticksElytraFlying < 4) { e.setY(0.2f); e.cancel(); return; }
            if (mc.options.jumpKey.isPressed()) e.setY(ySpeed.getValue() * Math.min((accelerationY += 9) / 100.0f, 1.0f));
            else if (mc.options.sneakKey.isPressed()) e.setY(-ySpeed.getValue() * Math.min((accelerationY += 9) / 100.0f, 1.0f));
            else if (bowBomb.getValue() && checkGround(2.0f)) e.setY(mc.player.age % 2 == 0 ? 0.42f : -0.42f);
            else switch (antiKick.getValue()) { case Jitter -> e.setY(mc.player.age % 2 == 0 ? 0.08f : -0.08f); case Glide -> e.setY(-0.08f); case Off -> e.setY(0f); }
            if (!MovementUtility.isMoving()) acceleration = 0;
            MovementUtility.modifyEventSpeed(e, xzSpeed.getValue() * Math.min((acceleration += 9) / 100.0f, 1.0f));
            if (stayMad.getValue() && !checkGround(3.0f) && Managers.PLAYER.ticksElytraFlying > 10) e.setY(0.42f);
            e.cancel();
        }
    }

    private boolean checkGround(float f) { return !mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(0.0, -f, 0.0)).iterator().hasNext(); }
    private void noFireworks() { disable(isRu() ? "Нету фейерверков в инвентаре!" : "No fireworks in the hotbar!"); flying = false; }
    private void noElytra() { disable(isRu() ? "Нету элитр в инвентаре!" : "No elytras found in the inventory!"); flying = false; }

    private void reset() { slotWithFireWorks = -1; prevItemInHand = Items.AIR; getStackInSlotCopy = null; }
    private void resetPrevItems() { prevElytraSlot = -1; prevArmorItem = Items.AIR; prevArmorItemCopy = null; }
    private int getFireWorks(boolean hotbar) { return hotbar ? InventoryUtility.findItemInHotBar(Items.FIREWORK_ROCKET).slot() : InventoryUtility.findItemInInventory(Items.FIREWORK_ROCKET).slot(); }
    private void returnItem() {
        if (slotWithFireWorks == -1 || getStackInSlotCopy == null || prevItemInHand == Items.FIREWORK_ROCKET || prevItemInHand == Items.AIR) return;
        int n2 = findInInventory(getStackInSlotCopy, prevItemInHand);
        n2 = n2 < 9 && n2 != -1 ? n2 + 36 : n2;
        clickSlot(n2); clickSlot(fireSlot.getValue() - 1 + 36); clickSlot(n2);
    }
    private void returnChestPlate() {
        if (prevElytraSlot != -1 && prevArmorItem != Items.AIR) {
            if (!elytraEquiped) return;
            ItemStack is = mc.player.getInventory().getStack(prevElytraSlot);
            boolean bl2 = is != ItemStack.EMPTY && !ItemStack.areItemsEqual(is, prevArmorItemCopy);
            int n2 = findInInventory(prevArmorItemCopy, prevArmorItem);
            n2 = n2 < 9 && n2 != -1 ? n2 + 36 : n2;
            if (mc.player.currentScreenHandler.getCursorStack().getItem() != Items.AIR) {
                clickSlot(6); if (prevElytraSlot != -1) clickSlot(prevElytraSlot); return;
            }
            if (n2 == -1) return;
            clickSlot(n2); clickSlot(6);
            if (!bl2) clickSlot(n2);
            else { int n4 = findEmpty(false); if (n4 != -1) clickSlot(n4); }
        }
        resetPrevItems();
    }

    public static int findInInventory(ItemStack stack, Item item) {
        if (stack == null) return -1;
        for (int i = 0; i < 45; i++) if (ItemStack.areItemsEqual(mc.player.getInventory().getStack(i), stack) && mc.player.getInventory().getStack(i).getItem() == item) return i;
        return -1;
    }

    public static int findEmpty(boolean hotbar) {
        for (int i = hotbar ? 0 : 9; i < (hotbar ? 9 : 45); i++) if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        return -1;
    }
}
