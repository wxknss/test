package thunder.hack.features.modules.player;

import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.RotationUtility;
import thunder.hack.utility.render.Render3DEngine;

import java.awt.Color;
import java.util.Comparator;

public class FunnyClicker extends Module {
    public FunnyClicker() {
        super("FunnyClicker", Category.PLAYER);
    }

    private final Setting<Integer> delay = new Setting<>("Delay", 50, 10, 500);
    private final Setting<Boolean> rotate = new Setting<>("Rotate", true);
    private final Setting<Boolean> render = new Setting<>("Render", true);

    private final Timer timer = new Timer();
    private BlockPos lastTarget = null;

    @Override
    public void onEnable() {
        timer.reset();
    }

    @Override
    public void onUpdate() {
        if (fullNullCheck()) return;

        BlockPos target = findNearestContainer();
        if (target == null) return;

        if (!timer.passedMs(delay.getValue())) return;

        if (rotate.getValue()) {
            float[] rotations = RotationUtility.getRotations(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
            sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(rotations[0], rotations[1], mc.player.isOnGround()));
        }

        BlockHitResult hit = new BlockHitResult(
            target.toCenterPos(),
            Direction.UP,
            target,
            false
        );

        sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, id));
        lastTarget = target;
        timer.reset();
    }

    @Override
    public void onRender3D(net.minecraft.client.util.math.MatrixStack stack) {
        if (!render.getValue() || lastTarget == null) return;
        Render3DEngine.drawBlockOutline(stack, lastTarget, Color.GREEN, 2.0f);
    }

    private BlockPos findNearestContainer() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos closest = null;
        double closestDist = 5.0;

        for (BlockPos pos : getNearbyBlocks(5)) {
            Block block = mc.world.getBlockState(pos).getBlock();
            if (block instanceof ChestBlock || block instanceof TrappedChestBlock || block instanceof ShulkerBoxBlock) {
                double dist = playerPos.getSquaredDistance(pos);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = pos;
                }
            }
        }
        return closest;
    }

    private java.util.List<BlockPos> getNearbyBlocks(int radius) {
        java.util.List<BlockPos> list = new java.util.ArrayList<>();
        BlockPos center = mc.player.getBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    list.add(center.add(x, y, z));
                }
            }
        }
        return list;
    }
}
