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
import thunder.hack.events.impl.EventMove;
import thunder.hack.events.impl.EventPostSync;
import thunder.hack.events.impl.EventSync;
import thunder.hack.events.impl.EventTick;
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
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.NCP);
    private final Setting<InteractionUtility.PlaceMode> placeMode = new Setting<>("PlaceMode", InteractionUtility.PlaceMode.Normal, v -> !mode.is(Mode.Grim) && !mode.is(Mode.Matrix) && !mode.is(Mode.Legit));
    private final Setting<Switch> autoSwitch = new Setting<>("Switch", Switch.Silent);
    private final Setting<Boolean> rotate = new Setting<>("Rotate", true);
    private final Setting<Boolean> lockY = new Setting<>("LockY", false);
    private final Setting<Boolean> onlyNotHoldingSpace = new Setting<>("OnlyNotHoldingSpace", false, v -> lockY.getValue());
    private final Setting<Boolean> autoJump = new Setting<>("AutoJump", false);
    private final Setting<Boolean> allowShift = new Setting<>("WorkWhileSneaking", false);
    private final Setting<Boolean> tower = new Setting<>("Tower", true, v -> !mode.is(Mode.Grim) && !mode.is(Mode.Matrix) && !mode.is(Mode.Legit));
    private final Setting<Boolean> safewalk = new Setting<>("SafeWalk", true, v -> !mode.is(Mode.Grim) && !mode.is(Mode.Matrix));
    private final Setting<Boolean> echestholding = new Setting<>("EchestHolding", false);
    private final Setting<SettingGroup> renderCategory = new Setting<>("Render", new SettingGroup(false, 0));
    private final Setting<Boolean> render = new Setting<>("Render", true).addToGroup(renderCategory);
    private final Setting<BlockAnimationUtility.BlockRenderMode> renderMode = new Setting<>("RenderMode", BlockAnimationUtility.BlockRenderMode.All).addToGroup(renderCategory);
    private final Setting<BlockAnimationUtility.BlockAnimationMode> animationMode = new Setting<>("BlockAnimationMode", BlockAnimationUtility.BlockAnimationMode.Fade).addToGroup(renderCategory);
    private final Setting<ColorSetting> renderFillColor = new Setting<>("RenderFillColor", new ColorSetting(HudEditor.getColor(0))).addToGroup(renderCategory);
    private final Setting<ColorSetting> renderLineColor = new Setting<>("RenderLineColor", new ColorSetting(HudEditor.getColor(0))).addToGroup(renderCategory);
    private final Setting<Integer> renderLineWidth = new Setting<>("RenderLineWidth", 2, 1, 5).addToGroup(renderCategory);
    private final Setting<Integer> matrixDelay = new Setting<>("MatrixDelay", 3, 1, 10, v -> mode.is(Mode.Matrix));
    private final Setting<Integer> legitDelay = new Setting<>("LegitDelay", 5, 1, 20, v -> mode.is(Mode.Legit));

    private enum Mode { NCP, StrictNCP, Grim, Matrix, Legit }
    private enum Switch { Normal, Silent, Inventory, None }

    private final Timer timer = new Timer();
    private final Timer placeTimer = new Timer();
    private BlockPosWithFacing currentblock;
    private int prevY;
    private float rotationYaw, rotationPitch;

    public Scaffold() {
        super("Scaffold", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        prevY = -999;
    }

    @EventHandler
    public void onMove(EventMove event) {
        if (fullNullCheck()) return;

        if (mode.is(Mode.Matrix)) {
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

    @EventHandler public void onTick(EventTick e) {
        if (mode.is(Mode.Grim) || mode.is(Mode.Matrix) || mode.is(Mode.Legit)) {
            preAction(); postAction();
        }
    }

    @EventHandler public void onPre(EventSync e) {
        if (!mode.is(Mode.Grim) && !mode.is(Mode.Matrix) && !mode.is(Mode.Legit)) preAction();
    }

    public void preAction() {
        currentblock = null;
        if (mc.player.isSneaking() && !allowShift.getValue()) return;
        if (prePlace(false) == -1) return;

        if (mc.options.jumpKey.isPressed() && !MovementUtility.isMoving()) prevY = (int) Math.floor(mc.player.getY() - 1);
        if (MovementUtility.isMoving() && autoJump.getValue()) {
            if (mc.options.jumpKey.isPressed()) { if (onlyNotHoldingSpace.getValue()) prevY = (int) Math.floor(mc.player.getY() - 1); }
            else if (mc.player.isOnGround()) mc.player.jump();
        }

        BlockPos bp = lockY.getValue() && prevY != -999 ? BlockPos.ofFloored(mc.player.getX(), prevY, mc.player.getZ()) : new BlockPos((int) Math.floor(mc.player.getX()), (int) Math.floor(mc.player.getY() - 1), (int) Math.floor(mc.player.getZ()));
        if (!mc.world.getBlockState(bp).isReplaceable()) return;
        currentblock = checkNearBlocksExtended(bp);
        if (currentblock != null) {
            float[] rots = InteractionUtility.calculateAngle(currentblock.position().toCenterPos());
            rotationYaw = rots[0]; rotationPitch = rots[1];
            if (mode.is(Mode.Matrix)) { rotationYaw = mc.player.getYaw() + 180f; rotationPitch = 72f; }
            else if (mode.is(Mode.Legit)) rotationPitch = 85f;

            if (rotate.getValue() && !mode.is(Mode.Grim) && !mode.is(Mode.Matrix) && !mode.is(Mode.Legit)) {
                mc.player.setYaw(rotationYaw); mc.player.setPitch(rotationPitch);
            }
            ModuleManager.rotations.fixRotation = rotationYaw;
        }
    }

    @EventHandler public void onPost(EventPostSync e) {
        if (!mode.is(Mode.Grim) && !mode.is(Mode.Matrix) && !mode.is(Mode.Legit)) postAction();
    }

    public void postAction() {
        if (currentblock == null) return;
        if (mode.is(Mode.Matrix) && !placeTimer.passedMs(matrixDelay.getValue())) return;
        if (mode.is(Mode.Legit) && !placeTimer.passedMs(legitDelay.getValue() * 50)) return;

        int prevItem = prePlace(true);
        if (prevItem != -1) {
            if (mc.player.input.jumping && !MovementUtility.isMoving() && tower.getValue() && !mode.is(Mode.Grim) && !mode.is(Mode.Matrix) && !mode.is(Mode.Legit)) {
                mc.player.setVelocity(0.0, 0.42, 0.0);
                if (timer.passedMs(1500)) { mc.player.setVelocity(mc.player.getVelocity().x, -0.28, mc.player.getVelocity().z); timer.reset(); }
            } else timer.reset();

            BlockHitResult bhr = new BlockHitResult(currentblock.position().toCenterPos(), currentblock.facing(), currentblock.position(), false);
            if (mode.is(Mode.Grim) || mode.is(Mode.Matrix) || mode.is(Mode.Legit)) sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), rotationYaw, rotationPitch, mc.player.isOnGround()));
            mc.interactionManager.interactBlock(mc.player, prevItem == -2 ? Hand.OFF_HAND : Hand.MAIN_HAND, bhr);
            mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(prevItem == -2 ? Hand.OFF_HAND : Hand.MAIN_HAND));
            prevY = currentblock.position().getY();
            if (mode.is(Mode.Grim) || mode.is(Mode.Matrix) || mode.is(Mode.Legit)) sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()));
            if (render.getValue()) BlockAnimationUtility.renderBlock(currentblock.position(), renderLineColor.getValue().getColorObject(), renderLineWidth.getValue(), renderFillColor.getValue().getColorObject(), animationMode.getValue(), renderMode.getValue());
            postPlace(prevItem);
            placeTimer.reset();
        }
    }

    // Вспомогательные методы (checkNearBlocksExtended, prePlace, postPlace, isOffsetBBEmpty) остаются без изменений
    // Скопируй их из предыдущей версии, они не менялись
}
