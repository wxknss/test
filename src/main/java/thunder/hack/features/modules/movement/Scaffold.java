package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import thunder.hack.events.impl.*;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.client.HudEditor;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.ColorSetting;
import thunder.hack.setting.impl.SettingGroup;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.InteractionUtility;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.MovementUtility;
import thunder.hack.utility.player.SearchInvResult;
import thunder.hack.utility.render.BlockAnimationUtility;

import static thunder.hack.utility.player.InteractionUtility.BlockPosWithFacing;
import static thunder.hack.utility.player.InteractionUtility.checkNearBlocks;

public class Scaffold extends Module {
    public enum Mode { NCP, StrictNCP, Grim }
    public enum RotationMode { Normal, Silent, Grim }
    public enum SwitchMode { Normal, Silent, Inventory, None }

    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Grim);
    private final Setting<RotationMode> rotationMode = new Setting<>("RotationMode", RotationMode.Grim);
    private final Setting<SwitchMode> autoSwitch = new Setting<>("Switch", SwitchMode.Silent);
    private final Setting<InteractionUtility.PlaceMode> placeMode = new Setting<>("PlaceMode", InteractionUtility.PlaceMode.Normal, v -> !mode.is(Mode.Grim));
    private final Setting<Boolean> lockY = new Setting<>("LockY", false);
    private final Setting<Boolean> autoJump = new Setting<>("AutoJump", false);
    private final Setting<Boolean> allowShift = new Setting<>("WorkWhileSneaking", false);
    private final Setting<Boolean> tower = new Setting<>("Tower", true, v -> !mode.is(Mode.Grim));
    private final Setting<Boolean> safewalk = new Setting<>("SafeWalk", true);
    private final Setting<Integer> blocksPerTick = new Setting<>("BlocksPerTick", 1, 1, 10);
    private final Setting<Integer> placeDelay = new Setting<>("PlaceDelay", 1, 0, 5);
    private final Setting<Boolean> grimBypass = new Setting<>("GrimBypass", true, v -> mode.is(Mode.Grim));

    // Render
    private final Setting<SettingGroup> renderCategory = new Setting<>("Render", new SettingGroup(false, 0));
    private final Setting<Boolean> render = new Setting<>("Render", true).addToGroup(renderCategory);
    private final Setting<BlockAnimationUtility.BlockRenderMode> renderMode = new Setting<>("RenderMode", BlockAnimationUtility.BlockRenderMode.All).addToGroup(renderCategory);
    private final Setting<BlockAnimationUtility.BlockAnimationMode> animationMode = new Setting<>("AnimationMode", BlockAnimationUtility.BlockAnimationMode.Fade).addToGroup(renderCategory);
    private final Setting<ColorSetting> renderFillColor = new Setting<>("RenderFillColor", new ColorSetting(HudEditor.getColor(0))).addToGroup(renderCategory);
    private final Setting<ColorSetting> renderLineColor = new Setting<>("RenderLineColor", new ColorSetting(HudEditor.getColor(0))).addToGroup(renderCategory);
    private final Setting<Integer> renderLineWidth = new Setting<>("RenderLineWidth", 2, 1, 5).addToGroup(renderCategory);

    private final Timer timer = new Timer();
    private final Timer placeTimer = new Timer();
    private BlockPosWithFacing currentblock;
    private int prevY;
    private int blocksPlacedThisTick = 0;
    private float lastYaw, lastPitch;

    public Scaffold() {
        super("Scaffold", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        prevY = -999;
        blocksPlacedThisTick = 0;
    }

    @EventHandler
    public void onMove(EventMove event) {
        if (fullNullCheck()) return;
        if (safewalk.getValue()) {
            handleSafeWalk(event);
        }
    }

    private void handleSafeWalk(EventMove event) {
        double x = event.getX();
        double z = event.getZ();
        if (mc.player.isOnGround() && !mc.player.noClip) {
            double inc = 0.05;
            while (x != 0 && isOffsetBBEmpty(x, 0))
                x = adjust(x, inc);
            while (z != 0 && isOffsetBBEmpty(0, z))
                z = adjust(z, inc);
        }
        event.setX(x);
        event.setZ(z);
        event.cancel();
    }

    private double adjust(double val, double inc) {
        if (val < inc && val >= -inc) return 0;
        return val > 0 ? val - inc : val + inc;
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mode.is(Mode.Grim)) {
            preAction();
            postAction();
        }
        blocksPlacedThisTick = 0;
    }

    @EventHandler
    public void onPre(EventSync e) {
        if (!mode.is(Mode.Grim))
            preAction();

        if (currentblock != null && rotationMode.getValue() != RotationMode.Normal) {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
            float[] rots = getRotations(currentblock);
            if (rotationMode.getValue() == RotationMode.Silent) {
                sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(rots[0], rots[1], mc.player.isOnGround()));
            } else {
                mc.player.setYaw(rots[0]);
                mc.player.setPitch(rots[1]);
            }
        }
    }

    @EventHandler
    public void onPostSync(EventPostSync e) {
        if (currentblock != null && rotationMode.getValue() != RotationMode.Normal) {
            mc.player.setYaw(lastYaw);
            mc.player.setPitch(lastPitch);
        }
        if (!mode.is(Mode.Grim))
            postAction();
    }

    private float[] getRotations(BlockPosWithFacing bp) {
        Vec3d hit = new Vec3d(bp.position().getX() + 0.5, bp.position().getY() + 0.5, bp.position().getZ() + 0.5)
                .add(new Vec3d(bp.facing().getUnitVector()).multiply(0.5));
        float[] rots = InteractionUtility.calculateAngle(hit);
        // Для Grim pitch строго 90° если смотрим вниз
        if (mode.is(Mode.Grim) && bp.facing().getAxis().isHorizontal()) {
            rots[1] = 90f;
        }
        // Микро-рандом для обхода DuplicateRotPlace
        rots[0] += (Math.random() - 0.5) * 0.6f;
        rots[1] += (Math.random() - 0.5) * 0.4f;
        return rots;
    }

    private void preAction() {
        currentblock = null;
        if (mc.player.isSneaking() && !allowShift.getValue()) return;
        if (prePlace(false) == -1) return;

        if (MovementUtility.isMoving() && autoJump.getValue() && !mc.options.jumpKey.isPressed() && mc.player.isOnGround())
            mc.player.jump();

        BlockPos target = lockY.getValue() && prevY != -999
                ? BlockPos.ofFloored(mc.player.getX(), prevY, mc.player.getZ())
                : new BlockPos((int) Math.floor(mc.player.getX()), (int) (Math.floor(mc.player.getY() - 1)), (int) Math.floor(mc.player.getZ()));

        if (!mc.world.getBlockState(target).isReplaceable()) return;
        currentblock = checkNearBlocksExtended(target);
    }

    private void postAction() {
        if (!placeTimer.passedMs(placeDelay.getValue() * 50L)) return;
        if (currentblock == null) return;
        if (isColliding()) return;

        int blocksToPlace = mode.is(Mode.Grim) ? 1 : blocksPerTick.getValue(); // Grim: только 1 блок за тик

        for (int i = 0; i < blocksToPlace; i++) {
            if (blocksPlacedThisTick >= blocksPerTick.getValue()) break;
            if (currentblock == null) break;

            int prevSlot = prePlace(true);
            if (prevSlot == -1) continue;

            // Tower
            if (tower.getValue() && mc.player.input.jumping && !MovementUtility.isMoving() && !mode.is(Mode.Grim)) {
                mc.player.setVelocity(0, 0.42, 0);
                if (timer.passedMs(1500)) {
                    mc.player.setVelocity(mc.player.getVelocity().x, -0.28, mc.player.getVelocity().z);
                    timer.reset();
                }
            } else timer.reset();

            BlockHitResult bhr = createHitResult();
            boolean sneak = InteractionUtility.needSneak(mc.world.getBlockState(bhr.getBlockPos()).getBlock()) && !mc.player.isSneaking();

            if (sneak)
                sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));

            // Для Grim отправляем поворот перед плейсом
            if (mode.is(Mode.Grim) && grimBypass.getValue()) {
                float[] rots = getRotations(currentblock);
                sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(rots[0], rots[1], mc.player.isOnGround()));
            }

            if (placeMode.getValue() == InteractionUtility.PlaceMode.Packet && !mode.is(Mode.Grim)) {
                boolean offhand = prevSlot == -2;
                sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND, bhr, id));
            } else {
                mc.interactionManager.interactBlock(mc.player, prevSlot == -2 ? Hand.OFF_HAND : Hand.MAIN_HAND, bhr);
            }

            sendPacket(new HandSwingC2SPacket(prevSlot == -2 ? Hand.OFF_HAND : Hand.MAIN_HAND));
            prevY = currentblock.position().getY();

            if (sneak)
                sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));

            if (render.getValue())
                BlockAnimationUtility.renderBlock(currentblock.position(), renderLineColor.getValue().getColorObject(), renderLineWidth.getValue(), renderFillColor.getValue().getColorObject(), animationMode.getValue(), renderMode.getValue());

            postPlace(prevSlot);
            blocksPlacedThisTick++;
            placeTimer.reset();

            if (i < blocksToPlace - 1)
                findNextBlock();
        }
    }

    private BlockHitResult createHitResult() {
        BlockPos pos = currentblock.position();
        if (mode.is(Mode.Grim)) {
            // Курсор строго в диапазоне [0.3, 0.7] для обхода FabricatedPlace
            double rx = 0.5 + (Math.random() - 0.5) * 0.4;
            double ry = 0.5 + (Math.random() - 0.5) * 0.4;
            double rz = 0.5 + (Math.random() - 0.5) * 0.4;
            return new BlockHitResult(new Vec3d(pos.getX() + rx, pos.getY() + ry, pos.getZ() + rz), currentblock.facing(), pos, false);
        }
        return new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.99, pos.getZ() + 0.5), currentblock.facing(), pos, false);
    }

    private boolean isColliding() {
        float offset = mode.is(Mode.Grim) ? 0.3f : 0.2f;
        return mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-offset, 0, -offset).offset(0, -0.5, 0)).iterator().hasNext();
    }

    private void findNextBlock() {
        BlockPos target = lockY.getValue() && prevY != -999
                ? BlockPos.ofFloored(mc.player.getX(), prevY, mc.player.getZ())
                : new BlockPos((int) Math.floor(mc.player.getX()), (int) (Math.floor(mc.player.getY() - 1)), (int) Math.floor(mc.player.getZ()));
        if (mc.world.getBlockState(target).isReplaceable())
            currentblock = checkNearBlocksExtended(target);
        else
            currentblock = null;
    }

    private BlockPosWithFacing checkNearBlocksExtended(BlockPos pos) {
        BlockPosWithFacing res = checkNearBlocks(pos);
        if (res != null) return res;
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) continue;
                res = checkNearBlocks(pos.add(x, 0, z));
                if (res != null) return res;
            }
        for (int y = -1; y <= 0; y++) {
            res = checkNearBlocks(pos.add(0, y, 0));
            if (res != null) return res;
            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) continue;
                    res = checkNearBlocks(pos.add(x, y, z));
                    if (res != null) return res;
                }
        }
        return null;
    }

    private int prePlace(boolean swap) {
        if (mc.player == null || mc.world == null) return -1;
        if (mc.player.getOffHandStack().getItem() instanceof BlockItem bi && !bi.getBlock().getDefaultState().isReplaceable())
            return -2;
        if (mc.player.getMainHandStack().getItem() instanceof BlockItem bi && !bi.getBlock().getDefaultState().isReplaceable())
            return mc.player.getInventory().selectedSlot;

        int prevSlot = mc.player.getInventory().selectedSlot;
        SearchInvResult hotbar = InventoryUtility.findInHotBar(i -> i.getItem() instanceof BlockItem bi && !bi.getBlock().getDefaultState().isReplaceable());
        SearchInvResult inv = InventoryUtility.findInInventory(i -> i.getItem() instanceof BlockItem bi && !bi.getBlock().getDefaultState().isReplaceable());

        if (swap) {
            switch (autoSwitch.getValue()) {
                case Inventory -> {
                    if (inv.found()) {
                        prevSlot = inv.slot();
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, prevSlot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
                        sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                    }
                }
                case Normal, Silent -> hotbar.switchTo();
            }
        }
        return prevSlot;
    }

    private void postPlace(int prevSlot) {
        if (prevSlot == -1 || prevSlot == -2) return;
        switch (autoSwitch.getValue()) {
            case Inventory -> {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, prevSlot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
                sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            }
            case Silent -> InventoryUtility.switchTo(prevSlot);
        }
    }

    private boolean isOffsetBBEmpty(double x, double z) {
        return !mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-0.1, 0, -0.1).offset(x, -2, z)).iterator().hasNext();
    }
}
