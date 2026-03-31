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
import java.util.Random;

public class OCScaffold extends Module {
    public OCScaffold() {
        super("OCScaffold", Category.MOVEMENT);
    }

    // ===== НАСТРОЙКИ =====
    private final Setting<Integer> delayMin = new Setting<>("DelayMin", 0, 0, 3);
    private final Setting<Integer> delayMax = new Setting<>("DelayMax", 0, 0, 3);
    private final Setting<Float> minDist = new Setting<>("MinDist", 0.0f, 0.0f, 0.25f);
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
    private final Setting<Boolean> autoBlock = new Setting<>("AutoBlock", true);
    private final Setting<Integer> expandLength = new Setting<>("ExpandLength", 3, 1, 5);
    private final Setting<Boolean> eagle = new Setting<>("Eagle", false);
    private final Setting<Boolean> down = new Setting<>("Down", false);
    private final Setting<Boolean> stabilize = new Setting<>("Stabilize", true);
    private final Setting<Boolean> tower = new Setting<>("Tower", true);
    private final Setting<Boolean> autoJump = new Setting<>("AutoJump", false);
    private final Setting<Boolean> center = new Setting<>("Center", false);
    private final Setting<Integer> blocksPerTick = new Setting<>("BlocksPerTick", 1, 1, 5);

    private enum Technique { Normal, Expand }
    private enum RotationMode { Stabilized, EdgePoint, Linear, None }
    private enum ResetMode { Reset, Reverse, None }
    private enum RotationTiming { Normal, OnTickSnap }

    private final Timer timer = new Timer();
    private final Random random = new Random();
    private final List<BlockPos> placedBlocks = new ArrayList<>();
    private BlockPos targetPos = null;
    private BlockPos lastPos = null;
    private float targetYaw = 0;
    private float targetPitch = 0;
    private int resetTicks = 0;
    private int straightTicksLeft = 0;
    private int jumpTicksLeft = 0;
    private int blocksPlaced = 0;
    private boolean wasOnGround = false;
    private boolean didJump = false;
    private boolean sentTeleport = false;
    private int ticksSincePlace = 0;

    @Override
    public void onEnable() {
        targetPos = null;
        lastPos = null;
        resetTicks = 0;
        straightTicksLeft = 0;
        jumpTicksLeft = 0;
        blocksPlaced = 0;
        wasOnGround = false;
        didJump = false;
        sentTeleport = false;
        ticksSincePlace = 0;
        placedBlocks.clear();
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (fullNullCheck()) return;
        
        thunder.hack.ThunderHack.TICK_TIMER = timerSpeed.getValue();
        
        // Center
        if (center.getValue() && mc.player.isOnGround() && MovementUtility.isMoving()) {
            double x = Math.floor(mc.player.getX()) + 0.5;
            double z = Math.floor(mc.player.getZ()) + 0.5;
            mc.player.setPosition(x, mc.player.getY(), z);
        }
        
        // Tower
        if (tower.getValue() && mc.options.jumpKey.isPressed() && !MovementUtility.isMoving() && mc.player.isOnGround()) {
            mc.player.jump();
            didJump = true;
        }
        
        // AutoJump
        if (autoJump.getValue() && MovementUtility.isMoving() && mc.player.isOnGround() && !wasOnGround) {
            mc.player.jump();
        }
        wasOnGround = mc.player.isOnGround();
        
        // Sprint
        if (clientSprint.getValue()) {
            mc.player.setSprinting(true);
        }
        if (serverSprint.getValue()) {
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
        }
        
        // AutoBlock - тихий выбор слота
        if (autoBlock.getValue()) {
            int slot = getBlockSlot();
            if (slot != -1 && slot != mc.player.getInventory().selectedSlot) {
                InventoryUtility.switchTo(slot);
            }
        }
        
        findBlocks();
        
        if (targetPos != null && canPlace()) {
            for (int i = 0; i < blocksPerTick.getValue(); i++) {
                if (targetPos == null) break;
                placeBlock();
                blocksPlaced++;
                if (i < blocksPerTick.getValue() - 1) {
                    findBlocks();
                }
            }
            blocksPlaced = 0;
        }
        
        if (ticksSincePlace > 0) ticksSincePlace--;
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (rotationTiming.getValue() == RotationTiming.Normal && targetPos != null && rotationMode.getValue() != RotationMode.None) {
            float[] rotations = getRotations(new Vec3d(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5));
            targetYaw = rotations[0];
            targetPitch = rotations[1];
            sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(targetYaw, targetPitch, mc.player.isOnGround()));
        }
    }

    @EventHandler
    public void onMove(EventMove e) {
        if (fullNullCheck()) return;
        
        // SameYFalling
        if (sameYFalling.getValue() && MovementUtility.isMoving() && mc.player.getVelocity().y < 0 && mc.player.isOnGround()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -0.1, mc.player.getVelocity().z);
        }
        
        // Eagle (авто-шифт на краю)
        if (eagle.getValue() && !down.getValue() && MovementUtility.isMoving() && isAtEdge()) {
            e.cancel();
            mc.player.setSneaking(true);
        }
        
        // Down
        if (down.getValue() && mc.options.sneakKey.isPressed() && isSafeToFall()) {
            e.cancel();
            mc.player.setSneaking(false);
        }
        
        if (didJump && mc.player.getVelocity().y < 0) {
            mc.player.setVelocity(mc.player.getVelocity().x, -0.1, mc.player.getVelocity().z);
            didJump = false;
        }
    }
    
    private void findBlocks() {
        targetPos = null;
        
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos below = playerPos.down();
        
        // Normal: блок под ногами
        if (technique.getValue() == Technique.Normal) {
            if (mc.world.getBlockState(below).isReplaceable()) {
                targetPos = below;
            }
        }
        
        // Expand: блок впереди
        if (targetPos == null && expandLength.getValue() > 0) {
            double yawRad = Math.toRadians(mc.player.getYaw());
            double dirX = -Math.sin(yawRad);
            double dirZ = Math.cos(yawRad);
            
            for (int i = 1; i <= expandLength.getValue(); i++) {
                BlockPos forward = playerPos.add((int) Math.round(dirX * i), -1, (int) Math.round(dirZ * i));
                if (mc.world.getBlockState(forward).isReplaceable()) {
                    targetPos = forward;
                    break;
                }
            }
        }
        
        // Telly: телепорт к блоку (если далеко)
        if (telly.getValue() && !sentTeleport && targetPos != null && mc.player.squaredDistanceTo(targetPos.getX(), targetPos.getY(), targetPos.getZ()) > 9) {
            sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(targetPos.getX() + 0.5, targetPos.getY() + 0.1, targetPos.getZ() + 0.5, false));
            sentTeleport = true;
        } else {
            sentTeleport = false;
        }
    }
    
    private boolean canPlace() {
        if (targetPos == null) return false;
        
        // Delay
        int delay = random.nextInt(delayMax.getValue() - delayMin.getValue() + 1) + delayMin.getValue();
        if (!timer.passedMs(delay)) return false;
        
        // StraightTicks
        if (straightTicksLeft > 0) {
            straightTicksLeft--;
            return false;
        }
        
        // JumpTicks
        if (jumpTicksLeft > 0) {
            jumpTicksLeft--;
            return false;
        }
        
        // RequiresSight
        if (requiresSight.getValue()) {
            BlockPos lookPos = BlockPos.ofFloored(targetPos.getX(), targetPos.getY() + 0.5, targetPos.getZ());
            if (!mc.player.canSee(lookPos.getX(), lookPos.getY(), lookPos.getZ())) {
                return false;
            }
        }
        
        // MinDist
        if (minDist.getValue() > 0) {
            Vec3d eyes = mc.player.getEyePos();
            Vec3d targetVec = new Vec3d(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
            double diffX = targetVec.x - eyes.x;
            double diffZ = targetVec.z - eyes.z;
            if (Math.abs(diffX) < minDist.getValue() || Math.abs(diffZ) < minDist.getValue()) {
                return false;
            }
        }
        
        // ResetMode
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
        
        // Rotations для OnTickSnap
        if (rotationTiming.getValue() == RotationTiming.OnTickSnap && rotationMode.getValue() != RotationMode.None) {
            float[] rotations = getRotations(hit.getPos());
            targetYaw = rotations[0];
            targetPitch = rotations[1];
            sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(targetYaw, targetPitch, mc.player.isOnGround()));
        }
        
        // Stabilize Movement
        if (stabilize.getValue() && MovementUtility.isMoving()) {
            stabilizeMovement(hit.getPos());
        }
        
        // Aim on Tower
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
        ticksSincePlace = 2;
        
        if (slot != prevSlot) {
            InventoryUtility.switchTo(prevSlot);
        }
    }
    
    private void stabilizeMovement(Vec3d hitPos) {
        Vec3d targetCenter = new Vec3d(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        Vec3d toTarget = targetCenter.subtract(mc.player.getPos());
        double angleToTarget = Math.toDegrees(Math.atan2(toTarget.z, toTarget.x));
        double currentYaw = mc.player.getYaw();
        double diff = Math.abs(angleToTarget - currentYaw);
        
        if (diff > 45) {
            double moveX = -Math.sin(Math.toRadians(currentYaw)) * 0.1;
            double moveZ = Math.cos(Math.toRadians(currentYaw)) * 0.1;
            mc.player.setVelocity(moveX, mc.player.getVelocity().y, moveZ);
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
    
    private boolean isAtEdge() {
        BlockPos pos = mc.player.getBlockPos();
        BlockPos below = pos.down();
        BlockPos forward = getForwardBlock();
        return mc.world.getBlockState(below).isAir() && !mc.world.getBlockState(forward).isAir();
    }
    
    private BlockPos getForwardBlock() {
        double yawRad = Math.toRadians(mc.player.getYaw());
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);
        return mc.player.getBlockPos().add((int) Math.round(dirX), 0, (int) Math.round(dirZ));
    }
    
    private boolean isSafeToFall() {
        BlockPos below = mc.player.getBlockPos().down(2);
        return mc.world.getBlockState(below).isSolid();
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
