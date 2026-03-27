package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import thunder.hack.events.impl.EventMove;
import thunder.hack.events.impl.EventSync;
import thunder.hack.events.impl.EventTick;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.client.HudEditor;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.ColorSetting;
import thunder.hack.setting.impl.SettingGroup;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.MovementUtility;
import thunder.hack.utility.player.SearchInvResult;
import thunder.hack.utility.render.BlockAnimationUtility;

import static thunder.hack.utility.player.InteractionUtility.BlockPosWithFacing;
import static thunder.hack.utility.player.InteractionUtility.checkNearBlocks;

public class Scaffold extends Module {
    public Scaffold() {
        super("Scaffold", Category.MOVEMENT);
    }

    // ===== ОСНОВНЫЕ НАСТРОЙКИ =====
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.NCP);
    private final Setting<PlaceMode> placeMode = new Setting<>("PlaceMode", PlaceMode.Normal);
    private final Setting<SwitchMode> autoSwitch = new Setting<>("Switch", SwitchMode.Silent);
    private final Setting<Boolean> rotate = new Setting<>("Rotate", true);
    private final Setting<Boolean> moveFix = new Setting<>("MoveFix", true);
    
    // ===== РАСШИРЕННЫЕ НАСТРОЙКИ =====
    private final Setting<Integer> cps = new Setting<>("CPS", 12, 1, 20);
    private final Setting<Integer> placeDelay = new Setting<>("PlaceDelay", 0, 0, 500);
    private final Setting<Boolean> expand = new Setting<>("Expand", false);
    private final Setting<Integer> expandRadius = new Setting<>("ExpandRadius", 2, 1, 5, v -> expand.getValue());
    private final Setting<Boolean> tower = new Setting<>("Tower", true);
    private final Setting<Float> towerSpeed = new Setting<>("TowerSpeed", 0.42f, 0.3f, 0.6f, v -> tower.getValue());
    private final Setting<Boolean> safewalk = new Setting<>("SafeWalk", true);
    private final Setting<Float> walkSpeed = new Setting<>("WalkSpeed", 0.98f, 0.5f, 1.0f);
    private final Setting<Boolean> lockY = new Setting<>("LockY", false);
    private final Setting<Integer> lockYHeight = new Setting<>("LockYHeight", 1, 0, 5, v -> lockY.getValue());
    private final Setting<Boolean> onlySpace = new Setting<>("OnlySpace", false);
    private final Setting<Boolean> autoJump = new Setting<>("AutoJump", false);
    private final Setting<Boolean> autoJumpOnEdge = new Setting<>("AutoJumpOnEdge", true, v -> autoJump.getValue());
    private final Setting<Boolean> allowShift = new Setting<>("WorkWhileSneaking", false);
    private final Setting<Boolean> echestHolding = new Setting<>("EchestHolding", false);
    
    // ===== РЕНДЕР =====
    private final Setting<SettingGroup> renderCategory = new Setting<>("Render", new SettingGroup(false, 0));
    private final Setting<Boolean> render = new Setting<>("Render", true).addToGroup(renderCategory);
    private final Setting<BlockAnimationUtility.BlockRenderMode> renderMode = new Setting<>("RenderMode", BlockAnimationUtility.BlockRenderMode.All).addToGroup(renderCategory);
    private final Setting<BlockAnimationUtility.BlockAnimationMode> animationMode = new Setting<>("AnimationMode", BlockAnimationUtility.BlockAnimationMode.Fade).addToGroup(renderCategory);
    private final Setting<ColorSetting> renderFillColor = new Setting<>("RenderFillColor", new ColorSetting(HudEditor.getColor(0))).addToGroup(renderCategory);
    private final Setting<ColorSetting> renderLineColor = new Setting<>("RenderLineColor", new ColorSetting(HudEditor.getColor(0))).addToGroup(renderCategory);
    private final Setting<Integer> renderLineWidth = new Setting<>("RenderLineWidth", 2, 1, 5).addToGroup(renderCategory);

    public enum Mode { NCP, StrictNCP, Grim }
    public enum PlaceMode { Normal, Packet }
    public enum SwitchMode { Normal, Silent, Inventory, None }

    private final Timer timer = new Timer();
    private final Timer placeTimer = new Timer();
    private BlockPosWithFacing currentBlock;
    private int prevY = -999;
    private boolean hasJumped = false;

    @Override
    public void onEnable() {
        prevY = -999;
        hasJumped = false;
    }

    @EventHandler
    public void onMove(EventMove e) {
        if (fullNullCheck()) return;

        // SafeWalk + MoveFix
        if (safewalk.getValue() && !mode.is(Mode.Grim)) {
            double x = e.getX();
            double y = e.getY();
            double z = e.getZ();

            if (mc.player.isOnGround() && !mc.player.noClip) {
                double increment = 0.05;
                while (x != 0.0D && isOffsetBBEmpty(x, 0.0D)) {
                    if (x < increment && x >= -increment) x = 0.0D;
                    else if (x > 0.0D) x -= increment;
                    else x += increment;
                }
                while (z != 0.0D && isOffsetBBEmpty(0.0D, z)) {
                    if (z < increment && z >= -increment) z = 0.0D;
                    else if (z > 0.0D) z -= increment;
                    else z += increment;
                }
            }
            
            if (moveFix.getValue()) {
                e.setX(x * walkSpeed.getValue());
                e.setZ(z * walkSpeed.getValue());
            } else {
                e.setX(x);
                e.setZ(z);
            }
            e.cancel();
        }
    }

    @EventHandler
    public void onUpdate(EventTick e) {
        if (fullNullCheck()) return;

        if (mc.player.isSneaking() && !allowShift.getValue()) return;
        if (onlySpace.getValue() && !mc.options.jumpKey.isPressed()) return;

        // AutoJump
        if (autoJump.getValue() && MovementUtility.isMoving() && mc.player.isOnGround()) {
            if (autoJumpOnEdge.getValue() && isOnEdge()) {
                mc.player.jump();
            } else if (!autoJumpOnEdge.getValue()) {
                mc.player.jump();
            }
        }

        // Поиск блока
        findBlock();
        
        // Постановка блока
        if (currentBlock != null && placeTimer.passedMs(placeDelay.getValue())) {
            if (placeTimer.passedMs(1000 / cps.getValue())) {
                placeBlock();
                placeTimer.reset();
            }
        }

        // Tower
        if (tower.getValue() && mc.options.jumpKey.isPressed() && !MovementUtility.isMoving() && mc.player.isOnGround()) {
            mc.player.setVelocity(0, towerSpeed.getValue(), 0);
            if (!hasJumped) {
                mc.player.jump();
                hasJumped = true;
            }
        } else {
            hasJumped = false;
        }

        // Lock Y
        if (lockY.getValue() && prevY != -999) {
            double targetY = prevY + lockYHeight.getValue();
            if (mc.player.getY() < targetY) {
                mc.player.setPosition(mc.player.getX(), targetY, mc.player.getZ());
            }
        }
    }

    private void findBlock() {
        currentBlock = null;

        BlockPos below = new BlockPos((int) Math.floor(mc.player.getX()), (int) (Math.floor(mc.player.getY() - 1)), (int) Math.floor(mc.player.getZ()));
        
        if (expand.getValue()) {
            for (int x = -expandRadius.getValue(); x <= expandRadius.getValue(); x++) {
                for (int z = -expandRadius.getValue(); z <= expandRadius.getValue(); z++) {
                    BlockPos pos = below.add(x, 0, z);
                    if (mc.world.getBlockState(pos).isReplaceable()) {
                        currentBlock = checkNearBlocksExtended(pos);
                        if (currentBlock != null) return;
                    }
                }
            }
        }
        
        if (mc.world.getBlockState(below).isReplaceable()) {
            currentBlock = checkNearBlocksExtended(below);
            if (currentBlock != null) return;
        }
        
        currentBlock = null;
    }

    private void placeBlock() {
        if (currentBlock == null) return;
        
        int slot = getBlockSlot();
        if (slot == -1) return;
        
        int prevSlot = mc.player.getInventory().selectedSlot;
        if (slot != prevSlot) {
            switch (autoSwitch.getValue()) {
                case Normal -> mc.player.getInventory().selectedSlot = slot;
                case Silent -> InventoryUtility.switchTo(slot);
                case Inventory -> {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, prevSlot, SlotActionType.SWAP, mc.player);
                    sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                }
            }
        }
        
        BlockHitResult bhr = new BlockHitResult(
            new Vec3d(currentBlock.position().getX() + 0.5, currentBlock.position().getY() + 0.5, currentBlock.position().getZ() + 0.5)
                .add(new Vec3d(currentBlock.facing().getUnitVector()).multiply(0.5)),
            currentBlock.facing(), currentBlock.position(), false
        );
        
        float[] rotations = InteractionUtility.calculateAngle(bhr.getPos());
        
        boolean sneak = InteractionUtility.needSneak(mc.world.getBlockState(bhr.getBlockPos()).getBlock()) && !mc.player.isSneaking();
        
        if (sneak) mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
        
        if (rotate.getValue()) {
            sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), rotations[0], rotations[1], mc.player.isOnGround()));
        }
        
        if (placeMode.getValue() == PlaceMode.Packet) {
            sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(slot == -2 ? Hand.OFF_HAND : Hand.MAIN_HAND, bhr, id));
        } else {
            mc.interactionManager.interactBlock(mc.player, slot == -2 ? Hand.OFF_HAND : Hand.MAIN_HAND, bhr);
        }
        
        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(slot == -2 ? Hand.OFF_HAND : Hand.MAIN_HAND));
        
        prevY = currentBlock.position().getY();
        
        if (sneak) mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
        
        if (rotate.getValue()) {
            sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()));
        }
        
        if (slot != prevSlot && autoSwitch.getValue() == SwitchMode.Silent) {
            InventoryUtility.switchTo(prevSlot);
        }
        
        if (render.getValue()) {
            BlockAnimationUtility.renderBlock(currentBlock.position(), renderLineColor.getValue().getColorObject(), renderLineWidth.getValue(), renderFillColor.getValue().getColorObject(), animationMode.getValue(), renderMode.getValue());
        }
    }

    private int getBlockSlot() {
        if (mc.player.getMainHandStack().getItem() instanceof BlockItem) {
            return mc.player.getInventory().selectedSlot;
        }
        if (mc.player.getOffHandStack().getItem() instanceof BlockItem && echestHolding.getValue()) {
            return -2;
        }
        SearchInvResult result = InventoryUtility.findInHotBar(i -> i.getItem() instanceof BlockItem);
        return result.found() ? result.slot() : -1;
    }

    private boolean isOnEdge() {
        return !mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(0, -0.5, 0)).iterator().hasNext();
    }

    private boolean isOffsetBBEmpty(double x, double z) {
        return !mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-0.1, 0, -0.1).offset(x, -2, z)).iterator().hasNext();
    }

    private BlockPosWithFacing checkNearBlocksExtended(BlockPos pos) {
        BlockPosWithFacing ret = checkNearBlocks(pos);
        if (ret != null) return ret;
        
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;
            ret = checkNearBlocks(pos.offset(dir));
            if (ret != null) return ret;
        }
        
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    ret = checkNearBlocks(pos.add(x, y, z));
                    if (ret != null) return ret;
                }
            }
        }
        
        return null;
    }
}
