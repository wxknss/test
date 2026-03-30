package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import thunder.hack.events.impl.EventMove;
import thunder.hack.events.impl.EventSync;
import thunder.hack.events.impl.EventTick;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.MovementUtility;
import thunder.hack.utility.player.SearchInvResult;

import java.util.ArrayList;
import java.util.List;

public class OCScaffold extends Module {
    public OCScaffold() {
        super("OCScaffold", Category.MOVEMENT);
    }

    private final Setting<Integer> blocksPerTick = new Setting<>("BlocksPerTick", 1, 1, 10);
    private final Setting<Integer> extendLength = new Setting<>("ExtendLength", 3, 1, 5);
    private final Setting<Boolean> tower = new Setting<>("Tower", true);
    private final Setting<Boolean> sprint = new Setting<>("Sprint", true);
    private final Setting<Boolean> rotate = new Setting<>("Rotate", true);
    private final Setting<Boolean> airSafe = new Setting<>("AirSafe", false);
    private final Setting<Boolean> onlySafe = new Setting<>("OnlySafe", false);
    private final Setting<Boolean> stopOnUnsafe = new Setting<>("StopOnUnsafe", false);
    private final Setting<Boolean> autoJump = new Setting<>("AutoJump", false);
    private final Setting<Float> timerSpeed = new Setting<>("Timer", 1.0f, 0.5f, 2.0f);
    private final Setting<Boolean> center = new Setting<>("Center", false);

    private final List<BlockPos> placedBlocks = new ArrayList<>();
    private BlockPos targetPos = null;
    private float targetYaw = 0;
    private float targetPitch = 0;
    private int blocksPlaced = 0;
    private boolean wasOnGround = false;
    private boolean didJump = false;
    private int tickCounter = 0;

    @Override
    public void onEnable() {
        targetPos = null;
        blocksPlaced = 0;
        wasOnGround = false;
        didJump = false;
        placedBlocks.clear();
        tickCounter = 0;
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (fullNullCheck()) return;
        
        thunder.hack.ThunderHack.TICK_TIMER = timerSpeed.getValue();
        
        if (center.getValue() && mc.player.isOnGround() && MovementUtility.isMoving()) {
            double x = Math.floor(mc.player.getX()) + 0.5;
            double z = Math.floor(mc.player.getZ()) + 0.5;
            mc.player.setPosition(x, mc.player.getY(), z);
        }
        
        if (tower.getValue() && mc.options.jumpKey.isPressed() && !MovementUtility.isMoving() && mc.player.isOnGround() && isSafeToTower()) {
            mc.player.jump();
            didJump = true;
        }
        
        if (autoJump.getValue() && MovementUtility.isMoving() && mc.player.isOnGround() && !wasOnGround) {
            mc.player.jump();
        }
        wasOnGround = mc.player.isOnGround();
        
        if (sprint.getValue()) {
            mc.player.setSprinting(true);
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
        }
        
        findBlocks();
        
        // Каждый тик ставим blocksPerTick блоков
        if (targetPos != null && canPlace()) {
            for (int i = 0; i < blocksPerTick.getValue(); i++) {
                if (targetPos == null) break;
                placeBlock();
                blocksPlaced++;
                // После каждой постановки ищем новый блок (если строим вперёд)
                if (i < blocksPerTick.getValue() - 1) {
                    findBlocks();
                }
            }
            blocksPlaced = 0;
        }
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (rotate.getValue() && targetPos != null) {
            float[] rotations = getRotations(new Vec3d(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5));
            targetYaw = rotations[0];
            targetPitch = rotations[1];
            sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(targetYaw, targetPitch, mc.player.isOnGround()));
        }
    }

    @EventHandler
    public void onMove(EventMove e) {
        if (fullNullCheck()) return;
        
        if (didJump && mc.player.getVelocity().y < 0) {
            mc.player.setVelocity(mc.player.getVelocity().x, -0.1, mc.player.getVelocity().z);
            didJump = false;
        }
    }
    
    private void findBlocks() {
        targetPos = null;
        
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos below = playerPos.down();
        
        if (mc.world.getBlockState(below).isReplaceable()) {
            targetPos = below;
        }
        
        if (targetPos == null && extendLength.getValue() > 0) {
            double yawRad = Math.toRadians(mc.player.getYaw());
            double dirX = -Math.sin(yawRad);
            double dirZ = Math.cos(yawRad);
            
            for (int i = 1; i <= extendLength.getValue(); i++) {
                BlockPos forward = playerPos.add((int)(dirX * i), -1, (int)(dirZ * i));
                if (mc.world.getBlockState(forward).isReplaceable()) {
                    targetPos = forward;
                    break;
                }
            }
        }
        
        if (targetPos != null && onlySafe.getValue() && !isSafe(targetPos)) {
            targetPos = null;
        }
        
        if (targetPos != null && airSafe.getValue() && !mc.world.getBlockState(targetPos.down()).isSolid()) {
            targetPos = null;
        }
        
        if (targetPos != null && stopOnUnsafe.getValue() && !isSafe(targetPos.down())) {
            disable();
        }
    }
    
    private boolean canPlace() {
        if (targetPos == null) return false;
        return true;
    }
    
    private void placeBlock() {
        if (targetPos == null) return;
        
        int slot = getBlockSlot();
        if (slot == -1) return;
        
        int prevSlot = mc.player.getInventory().selectedSlot;
        if (slot != prevSlot) {
            InventoryUtility.switchTo(slot);
        }
        
        Direction side = getPlaceSide(targetPos);
        if (side == null) return;
        
        BlockHitResult hit = new BlockHitResult(
            new Vec3d(targetPos.getX() + 0.5 + side.getOffsetX() * 0.5,
                      targetPos.getY() + 0.5 + side.getOffsetY() * 0.5,
                      targetPos.getZ() + 0.5 + side.getOffsetZ() * 0.5),
            side.getOpposite(),
            targetPos.offset(side),
            false
        );
        
        sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, id));
        mc.player.swingHand(Hand.MAIN_HAND);
        
        placedBlocks.add(targetPos);
        if (placedBlocks.size() > 10) placedBlocks.remove(0);
        
        if (slot != prevSlot) {
            InventoryUtility.switchTo(prevSlot);
        }
    }
    
    private Direction getPlaceSide(BlockPos pos) {
        if (mc.world.getBlockState(pos.down()).isSolid()) return Direction.UP;
        if (mc.world.getBlockState(pos.up()).isSolid()) return Direction.DOWN;
        if (mc.world.getBlockState(pos.north()).isSolid()) return Direction.SOUTH;
        if (mc.world.getBlockState(pos.south()).isSolid()) return Direction.NORTH;
        if (mc.world.getBlockState(pos.west()).isSolid()) return Direction.EAST;
        if (mc.world.getBlockState(pos.east()).isSolid()) return Direction.WEST;
        return null;
    }
    
    private int getBlockSlot() {
        if (mc.player.getMainHandStack().getItem() instanceof BlockItem) {
            return mc.player.getInventory().selectedSlot;
        }
        SearchInvResult result = InventoryUtility.findInHotBar(i -> i.getItem() instanceof BlockItem);
        if (result.found()) return result.slot();
        result = InventoryUtility.findInInventory(i -> i.getItem() instanceof BlockItem);
        if (result.found()) return result.slot();
        return -1;
    }
    
    private boolean isSafe(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();
        return block == Blocks.OBSIDIAN || block == Blocks.ENDER_CHEST || block == Blocks.ANVIL || 
               block == Blocks.ENCHANTING_TABLE || block == Blocks.CRAFTING_TABLE || block == Blocks.FURNACE ||
               block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || state.isSolid();
    }
    
    private boolean isSafeToTower() {
        BlockPos pos = mc.player.getBlockPos();
        for (int i = 1; i <= 3; i++) {
            BlockPos checkPos = pos.up(i);
            if (!mc.world.getBlockState(checkPos).isAir()) {
                return false;
            }
        }
        return true;
    }
    
    private float[] getRotations(Vec3d target) {
        Vec3d eyes = mc.player.getEyePos();
        double diffX = target.x - eyes.x;
        double diffY = target.y - eyes.y;
        double diffZ = target.z - eyes.z;
        double yaw = Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
        double pitch = -Math.toDegrees(Math.atan2(diffY, Math.hypot(diffX, diffZ)));
        return new float[]{(float) yaw, (float) pitch};
    }
}
