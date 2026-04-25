package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import thunder.hack.core.manager.client.ModuleManager;
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
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Matrix);
    private final Setting<InteractionUtility.PlaceMode> placeMode = new Setting<>("PlaceMode", InteractionUtility.PlaceMode.Normal, v -> !mode.is(Mode.Grim) && !mode.is(Mode.Matrix) && !mode.is(Mode.Legit));
    private final Setting<Switch> autoSwitch = new Setting<>("Switch", Switch.Silent);
    private final Setting<Boolean> rotate = new Setting<>("Rotate", true);
    private final Setting<Boolean> safewalk = new Setting<>("SafeWalk", true, v -> !mode.is(Mode.Grim) && !mode.is(Mode.Matrix));
    private final Setting<Boolean> tower = new Setting<>("Tower", true, v -> !mode.is(Mode.Grim) && !mode.is(Mode.Matrix) && !mode.is(Mode.Legit));
    private final Setting<SettingGroup> renderCategory = new Setting<>("Render", new SettingGroup(false, 0));
    private final Setting<Boolean> render = new Setting<>("Render", true).addToGroup(renderCategory);
    private final Setting<BlockAnimationUtility.BlockRenderMode> renderMode = new Setting<>("RenderMode", BlockAnimationUtility.BlockRenderMode.All).addToGroup(renderCategory);
    private final Setting<BlockAnimationUtility.BlockAnimationMode> animationMode = new Setting<>("BlockAnimationMode", BlockAnimationUtility.BlockAnimationMode.Fade).addToGroup(renderCategory);
    private final Setting<ColorSetting> renderFillColor = new Setting<>("RenderFillColor", new ColorSetting(HudEditor.getColor(0))).addToGroup(renderCategory);
    private final Setting<ColorSetting> renderLineColor = new Setting<>("RenderLineColor", new ColorSetting(HudEditor.getColor(0))).addToGroup(renderCategory);
    private final Setting<Integer> renderLineWidth = new Setting<>("RenderLineWidth", 2, 1, 5).addToGroup(renderCategory);
    private final Setting<Integer> matrixDelay = new Setting<>("MatrixDelay", 0, 0, 10, v -> mode.is(Mode.Matrix));
    private final Setting<Integer> legitDelay = new Setting<>("LegitDelay", 3, 1, 20, v -> mode.is(Mode.Legit));
    private final Setting<Float> matrixPitch = new Setting<>("MatrixPitch", 82f, 45f, 90f, v -> mode.is(Mode.Matrix));
    private final Setting<Float> legitPitch = new Setting<>("LegitPitch", 82f, 45f, 90f, v -> mode.is(Mode.Legit));

    private enum Mode { NCP, StrictNCP, Grim, Matrix, Legit }
    private enum Switch { Normal, Silent, Inventory, None }

    private final Timer timer = new Timer();
    private final Timer placeTimer = new Timer();
    private BlockPosWithFacing currentblock;
    private float rotationYaw, rotationPitch;

    public Scaffold() {
        super("Scaffold", Category.MOVEMENT);
    }

    @EventHandler
    public void onMove(EventMove event) {
        if (fullNullCheck()) return;

        if (mode.is(Mode.Matrix) || mode.is(Mode.Legit)) {
            if (mc.player.isOnGround() && mc.options.backKey.isPressed()) {
                MovementUtility.setMotion(0.18);
            }
        }

        if (safewalk.getValue() && !mode.is(Mode.Grim) && !mode.is(Mode.Matrix)) {
            double x = event.getX(), y = event.getY(), z = event.getZ();
            if (mc.player.isOnGround() && !mc.player.noClip) {
                double inc = 0.05;
                while (x != 0 && isOffsetBBEmpty(x, 0)) x = Math.abs(x) < inc ? 0 : x > 0 ? x - inc : x + inc;
                while (z != 0 && isOffsetBBEmpty(0, z)) z = Math.abs(z) < inc ? 0 : z > 0 ? z - inc : z + inc;
                while (x != 0 && z != 0 && isOffsetBBEmpty(x, z)) {
                    x = Math.abs(x) < inc ? 0 : x > 0 ? x - inc : x + inc;
                    z = Math.abs(z) < inc ? 0 : z > 0 ? z - inc : z + inc;
                }
            }
            event.setX(x); event.setY(y); event.setZ(z); event.cancel();
        }
    }

    @EventHandler
    public void onPre(EventSync e) {
        currentblock = null;
        if (!mode.is(Mode.Grim) && !mode.is(Mode.Matrix) && !mode.is(Mode.Legit)) return;
        if (prePlace(false) == -1) return;

        BlockPos bp = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 1, mc.player.getZ());
        if (!mc.world.getBlockState(bp).isReplaceable()) return;
        currentblock = checkNearBlocksExtended(bp);
        if (currentblock != null) {
            float[] rots = InteractionUtility.calculateAngle(currentblock.position().toCenterPos());
            rotationYaw = rots[0]; rotationPitch = rots[1];
            if (mode.is(Mode.Matrix)) { rotationYaw = mc.player.getYaw() + 180f; rotationPitch = matrixPitch.getValue(); }
            else if (mode.is(Mode.Legit)) rotationPitch = legitPitch.getValue();
            ModuleManager.rotations.fixRotation = rotationYaw;
        }
    }

    @EventHandler
    public void onPost(EventPostSync e) {
        if (currentblock == null) return;
        if (mode.is(Mode.Matrix) && !placeTimer.passedMs(matrixDelay.getValue())) return;
        if (mode.is(Mode.Legit) && !placeTimer.passedMs(legitDelay.getValue() * 50)) return;

        int prevItem = prePlace(true);
        if (prevItem != -1) {
            BlockHitResult bhr = new BlockHitResult(currentblock.position().toCenterPos(), currentblock.facing(), currentblock.position(), false);
            if (mode.is(Mode.Grim) || mode.is(Mode.Matrix) || mode.is(Mode.Legit))
                sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), rotationYaw, rotationPitch, mc.player.isOnGround()));
            mc.interactionManager.interactBlock(mc.player, prevItem == -2 ? Hand.OFF_HAND : Hand.MAIN_HAND, bhr);
            mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(prevItem == -2 ? Hand.OFF_HAND : Hand.MAIN_HAND));
            if (mode.is(Mode.Grim) || mode.is(Mode.Matrix) || mode.is(Mode.Legit))
                sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()));
            if (render.getValue()) BlockAnimationUtility.renderBlock(currentblock.position(), renderLineColor.getValue().getColorObject(), renderLineWidth.getValue(), renderFillColor.getValue().getColorObject(), animationMode.getValue(), renderMode.getValue());
            postPlace(prevItem);
            placeTimer.reset();
        }
    }

    private BlockPosWithFacing checkNearBlocksExtended(BlockPos pos) {
        BlockPosWithFacing r = checkNearBlocks(pos); if (r != null) return r;
        r = checkNearBlocks(pos.add(-1, 0, 0)); if (r != null) return r;
        r = checkNearBlocks(pos.add(1, 0, 0)); if (r != null) return r;
        r = checkNearBlocks(pos.add(0, 0, 1)); if (r != null) return r;
        r = checkNearBlocks(pos.add(0, 0, -1)); if (r != null) return r;
        return checkNearBlocks(pos.add(0, -1, 0));
    }

    private int prePlace(boolean swap) {
        if (mc.player.getOffHandStack().getItem() instanceof BlockItem bi && !bi.getBlock().getDefaultState().isReplaceable()) return -2;
        if (mc.player.getMainHandStack().getItem() instanceof BlockItem bi && !bi.getBlock().getDefaultState().isReplaceable()) return mc.player.getInventory().selectedSlot;

        int prev = mc.player.getInventory().selectedSlot;
        SearchInvResult hotbar = InventoryUtility.findInHotBar(i -> i.getItem() instanceof BlockItem bi && !bi.getBlock().getDefaultState().isReplaceable());
        if (swap) { if (autoSwitch.getValue() != Switch.None) hotbar.switchTo(); }
        return prev;
    }

    private void postPlace(int prev) {
        if (prev == -1 || prev == -2) return;
        if (autoSwitch.getValue() == Switch.Silent) InventoryUtility.switchTo(prev);
    }

    private boolean isOffsetBBEmpty(double x, double z) {
        return !mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-0.1, 0, -0.1).offset(x, -2, z)).iterator().hasNext();
    }
}
