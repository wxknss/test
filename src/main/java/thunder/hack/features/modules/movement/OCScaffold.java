package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BlockItem;
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

    private final Setting<Integer> minDelay = new Setting<>("MinDelay", 0, 0, 3);
    private final Setting<Integer> maxDelay = new Setting<>("MaxDelay", 0, 0, 3);
    private final Setting<Float> timerSpeed = new Setting<>("Timer", 1.0f, 0.5f, 2.0f);
    private final Setting<Technique> technique = new Setting<>("Technique", Technique.Normal);
    private final Setting<RotationMode> rotationMode = new Setting<>("RotationMode", RotationMode.Stabilized);
    private final Setting<Boolean> requiresSight = new Setting<>("RequiresSight", true);
    private final Setting<Boolean> telly = new Setting<>("Telly", true);
    private final Setting<ResetMode> resetMode = new Setting<>("ResetMode", ResetMode.Reset);
    private final Setting<Integer> straightTicks = new Setting<>("StraightTicks", 0, 0, 5);
    private final Setting<Integer> jumpTicks = new Setting<>("JumpTicks", 3, 0, 10);
    private final Setting<Boolean> aimOnTower = new Setting<>("AimOnTower", true);
    private final Setting<Boolean> sameYFalling = new Setting<>("SameYFalling", true);
    private final Setting<RotationTiming> rotationTiming = new Setting<>("RotationTiming", RotationTiming.Normal);
    private final Setting<Boolean> clientSprint = new Setting<>("ClientSprint", false);
    private final Setting<Boolean> serverSprint = new Setting<>("ServerSprint", false);

    private enum Technique { Normal, Teleport, Reverse }
    private enum RotationMode { Stabilized, EdgePoint, Linear, None }
    private enum ResetMode { Reset, Reverse, None }
    private enum RotationTiming { Normal, OnTickSnap }

    private final Timer timer = new Timer();
    private final List<BlockPos> placedBlocks = new ArrayList<>();
    private BlockPos targetPos = null;
    private BlockPos lastPos = null;
    private float targetYaw = 0;
    private float targetPitch = 0;
    private int resetTicks = 0;
    private int straightTicksLeft = 0;
    private int jumpTicksLeft = 0;
    private boolean sentTeleport = false;

    @Override
    public void onEnable() {
        targetPos = null;
        lastPos = null;
        resetTicks = 0;
        straightTicksLeft = 0;
        jumpTicksLeft = 0;
        sentTeleport = false;
        placedBlocks.clear();
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (fullNullCheck()) return;
        
        thunder.hack.ThunderHack.TICK_TIMER = timerSpeed.getValue();
        
        if (rotationTiming.getValue() == RotationTiming.OnTickSnap && targetPos != null) {
            sendRotation();
        }
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (rotationTiming.getValue() == RotationTiming.Normal && targetPos != null) {
            sendRotation();
        }
    }

    @EventHandler
    public void onMove(EventMove e) {
        if (fullNullCheck()) return;
        
        if (MovementUtility.isMoving() && sameYFalling.getValue() && mc.player.getVelocity().y < 0 && mc.player.isOnGround()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -0.1, mc.player.getVelocity().z);
        }
        
        if (clientSprint.getValue()) {
            mc.player.setSprinting(true);
        }
        if (serverSprint.getValue()) {
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
        }
        
        findBlock();
        
        if (targetPos != null && canPlace()) {
            placeBlock();
        }
    }
    
    private void findBlock() {
        BlockPos below = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 0.5, mc.player.getZ());
        BlockPos under = below.down();
        
        if (technique.getValue() == Technique.Reverse) {
            if (MovementUtility.isMoving() && !mc.world.getBlockState(under).isAir()) {
                targetPos = under.up();
            } else {
                targetPos = below;
            }
        } else {
            targetPos = below;
        }
        
        if (targetPos != null && !mc.world.getBlockState(targetPos).isReplaceable()) {
            targetPos = null;
        }
        
        if (telly.getValue() && !sentTeleport && targetPos != null && mc.player.squaredDistanceTo(targetPos.getX(), targetPos.getY(), targetPos.getZ()) > 9) {
            sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(targetPos.getX() + 0.5, targetPos.getY() + 0.1, targetPos.getZ() + 0.5, false));
            sentTeleport = true;
        } else {
            sentTeleport = false;
        }
    }
    
    private boolean canPlace() {
        int delay = (int) (Math.random() * (maxDelay.getValue() - minDelay.getValue() + 1) + minDelay.getValue());
        if (!timer.passedMs(delay)) return false;
        
        if (straightTicksLeft > 0) {
            straightTicksLeft--;
            return false;
        }
        
        if (jumpTicksLeft > 0) {
            jumpTicksLeft--;
            return false;
        }
        
        if (requiresSight.getValue()) {
            BlockPos lookPos = BlockPos.ofFloored(targetPos.getX(), targetPos.getY() + 0.5, targetPos.getZ());
            if (!mc.player.canSee(lookPos.getX(), lookPos.getY(), lookPos.getZ())) {
                return false;
            }
        }
        
        if (resetMode.getValue() != ResetMode.None && lastPos != null && lastPos.equals(targetPos)) {
            resetTicks++;
            if ((resetMode.getValue() == ResetMode.Reset && resetTicks > 3) ||
                (resetMode.getValue() == ResetMode.Reverse && resetTicks > 5)) {
                if (straightTicksLeft <= 0) straightTicksLeft = straightTicks.getValue();
                if (jumpTicksLeft <= 0) jumpTicksLeft = jumpTicks.getValue();
                resetTicks = 0;
                return false;
            }
        } else {
            resetTicks = 0;
        }
        
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
        
        if (rotationMode.getValue() != RotationMode.None) {
            float[] rotations = getRotations(hit.getPos());
            targetYaw = rotations[0];
            targetPitch = rotations[1];
            
            if (rotationMode.getValue() == RotationMode.Stabilized) {
                float yawDiff = Math.abs(mc.player.getYaw() - targetYaw);
                float pitchDiff = Math.abs(mc.player.getPitch() - targetPitch);
                if (yawDiff > 45 || pitchDiff > 45) {
                    targetYaw = mc.player.getYaw();
                    targetPitch = mc.player.getPitch();
                }
            } else if (rotationMode.getValue() == RotationMode.EdgePoint) {
                targetPitch = Math.min(85, targetPitch + 5);
            }
            
            if (rotationTiming.getValue() == RotationTiming.Normal) {
                mc.player.setYaw(targetYaw);
                mc.player.setPitch(targetPitch);
            }
        }
        
        if (technique.getValue() == Technique.Teleport) {
            sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false));
        }
        
        if (aimOnTower.getValue() && mc.options.jumpKey.isPressed() && !MovementUtility.isMoving()) {
            float[] rotations = getRotations(new Vec3d(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5));
            mc.player.setYaw(rotations[0]);
            mc.player.setPitch(rotations[1]);
        }
        
        sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, id));
        mc.player.swingHand(Hand.MAIN_HAND);
        
        placedBlocks.add(targetPos);
        if (placedBlocks.size() > 10) placedBlocks.remove(0);
        
        lastPos = targetPos;
        timer.reset();
        
        if (slot != prevSlot) {
            InventoryUtility.switchTo(prevSlot);
        }
    }
    
    private void sendRotation() {
        sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(targetYaw, targetPitch, mc.player.isOnGround()));
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
