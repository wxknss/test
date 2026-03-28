package thunder.hack.features.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.render.Render3DEngine;

import java.awt.Color;

public class Clicker extends Module {
    public Clicker() {
        super("Clicker", Category.PLAYER);
    }

    private final Setting<Integer> delay = new Setting<>("Delay", 50, 10, 500);
    private final Setting<Boolean> rotate = new Setting<>("Rotate", true);
    private final Setting<Boolean> cancelGui = new Setting<>("CancelGUI", true);
    private final Setting<Boolean> render = new Setting<>("Render", true);

    private final Timer timer = new Timer();
    private BlockPos lastTarget = null;

    @Override
    public void onEnable() {
        timer.reset();
        lastTarget = null;
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (cancelGui.getValue() && e.getPacket() instanceof OpenScreenS2CPacket) {
            e.cancel();
        }
    }

    @Override
    public void onUpdate() {
        if (fullNullCheck()) return;

        BlockPos target = findNearestContainer();
        if (target == null) {
            lastTarget = null;
            return;
        }

        if (!timer.passedMs(delay.getValue())) return;

        if (rotate.getValue()) {
            float[] rotations = getRotations(target);
            sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(rotations[0], rotations[1], mc.player.isOnGround()));
        }

        BlockHitResult hit = new BlockHitResult(
            new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5),
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
        Render3DEngine.drawBlockBox(lastTarget, Color.GREEN, 2.0f, true);
    }

    private BlockPos findNearestContainer() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos closest = null;
        double closestDist = 36.0;

        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    Block block = mc.world.getBlockState(pos).getBlock();
                    if (block instanceof ChestBlock || block instanceof TrappedChestBlock || block instanceof ShulkerBoxBlock) {
                        double dist = playerPos.getSquaredDistance(pos);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closest = pos;
                        }
                    }
                }
            }
        }
        return closest;
    }

    private float[] getRotations(BlockPos target) {
        Vec3d vec = new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        Vec3d eyes = mc.player.getEyePos();
        double diffX = vec.x - eyes.x;
        double diffY = vec.y - eyes.y;
        double diffZ = vec.z - eyes.z;

        double yaw = Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
        double pitch = -Math.toDegrees(Math.atan2(diffY, Math.hypot(diffX, diffZ)));

        return new float[]{(float) yaw, (float) pitch};
    }
}
