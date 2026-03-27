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
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.MovementUtility;
import thunder.hack.utility.player.SearchInvResult;

import static thunder.hack.utility.player.InteractionUtility.BlockPosWithFacing;
import static thunder.hack.utility.player.InteractionUtility.checkNearBlocks;

public class Scaffold extends Module {
    public Scaffold() {
        super("Scaffold", Category.MOVEMENT);
    }

    private final Setting<Float> speed = new Setting<>("Speed", 0.98f, 0.8f, 1.0f);
    private final Setting<Integer> cps = new Setting<>("CPS", 12, 1, 20);
    private final Setting<Boolean> lockY = new Setting<>("LockY", true);
    
    private final Timer placeTimer = new Timer();
    private BlockPosWithFacing targetBlock = null;
    private int targetY = -1;
    
    @Override
    public void onUpdate() {
        if (fullNullCheck()) return;
        
        // Lock Y
        if (lockY.getValue() && targetY != -1) {
            double dy = targetY - mc.player.getY();
            if (Math.abs(dy) > 0.1) {
                mc.player.setPosition(mc.player.getX(), targetY, mc.player.getZ());
            }
        }
        
        // Поиск блока для постановки
        BlockPos below = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 1, mc.player.getZ());
        if (mc.world.getBlockState(below).isReplaceable()) {
            targetBlock = findValidBlock(below);
        } else {
            targetBlock = null;
            targetY = (int) mc.player.getY();
        }
        
        // Постановка блока
        if (targetBlock != null && placeTimer.passedMs(1000 / cps.getValue())) {
            placeBlock(targetBlock);
            placeTimer.reset();
        }
    }
    
    @EventHandler
    public void onMove(EventMove e) {
        if (fullNullCheck()) return;
        
        // Замедление для легитного движения
        if (MovementUtility.isMoving()) {
            e.setX(e.getX() * speed.getValue());
            e.setZ(e.getZ() * speed.getValue());
            e.cancel();
        }
    }
    
    @EventHandler
    public void onSync(EventSync e) {
        if (targetBlock != null && targetY != -1) {
            // Серверные ротации (строго на видимую часть блока)
            Vec3d hitVec = new Vec3d(
                targetBlock.position().getX() + 0.5,
                targetBlock.position().getY() + 0.5,
                targetBlock.position().getZ() + 0.5
            ).add(new Vec3d(targetBlock.facing().getUnitVector()).multiply(0.5));
            
            float[] rotations = getRotations(hitVec);
            
            // Отправляем пакет с ротациями
            sendPacket(new PlayerMoveC2SPacket.Full(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                rotations[0], rotations[1], mc.player.isOnGround()
            ));
        }
    }
    
    private BlockPosWithFacing findValidBlock(BlockPos below) {
        // Сначала проверяем блок под ногами
        BlockPosWithFacing result = checkNearBlocks(below);
        if (result != null && isFacingVisible(result)) return result;
        
        // Проверяем соседние блоки
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;
            result = checkNearBlocks(below.offset(dir));
            if (result != null && isFacingVisible(result)) return result;
        }
        return null;
    }
    
    private boolean isFacingVisible(BlockPosWithFacing b) {
        // Проверяем, видна ли сторона блока, на которую целится
        BlockPos neighbor = b.position().offset(b.facing());
        return mc.world.getBlockState(neighbor).isAir();
    }
    
    private void placeBlock(BlockPosWithFacing b) {
        // Поиск блока в руке
        int slot = getBlockSlot();
        if (slot == -1) return;
        
        // Смена слота
        int prevSlot = mc.player.getInventory().selectedSlot;
        if (slot != prevSlot) {
            InventoryUtility.switchTo(slot);
        }
        
        // Пакет взаимодействия
        BlockHitResult bhr = new BlockHitResult(
            new Vec3d(b.position().getX() + 0.5, b.position().getY() + 0.5, b.position().getZ() + 0.5)
                .add(new Vec3d(b.facing().getUnitVector()).multiply(0.5)),
            b.facing(), b.position(), false
        );
        
        sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, bhr, id));
        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        
        // Возврат слота
        if (slot != prevSlot) {
            InventoryUtility.switchTo(prevSlot);
        }
    }
    
    private int getBlockSlot() {
        if (mc.player.getMainHandStack().getItem() instanceof BlockItem) {
            return mc.player.getInventory().selectedSlot;
        }
        SearchInvResult result = InventoryUtility.findInHotBar(i -> i.getItem() instanceof BlockItem);
        return result.found() ? result.slot() : -1;
    }
    
    private float[] getRotations(Vec3d target) {
        Vec3d diff = target.subtract(mc.player.getEyePos());
        double yaw = Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90;
        double pitch = -Math.toDegrees(Math.atan2(diff.y, Math.hypot(diff.x, diff.z)));
        return new float[]{(float) yaw, (float) pitch};
    }
}
