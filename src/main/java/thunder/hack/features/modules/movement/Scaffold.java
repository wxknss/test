package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
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
    private final Setting<InteractionUtility.PlaceMode> placeMode = new Setting<>("PlaceMode", InteractionUtility.PlaceMode.Normal, v -> !mode.is(Mode.Grim) && !mode.is(Mode.Sofia));
    private final Setting<Switch> autoSwitch = new Setting<>("Switch", Switch.Silent);
    private final Setting<Boolean> rotate = new Setting<>("Rotate", true);
    private final Setting<Boolean> lockY = new Setting<>("LockY", false);
    private final Setting<Boolean> onlyNotHoldingSpace = new Setting<>("OnlyNotHoldingSpace", false, v -> lockY.getValue());
    private final Setting<Boolean> autoJump = new Setting<>("AutoJump", false);
    private final Setting<Boolean> allowShift = new Setting<>("WorkWhileSneaking", false);
    private final Setting<Boolean> tower = new Setting<>("Tower", true, v -> !mode.is(Mode.Grim) && !mode.is(Mode.Sofia));
    private final Setting<Boolean> safewalk = new Setting<>("SafeWalk", true, v -> !mode.is(Mode.Grim) && !mode.is(Mode.Sofia));
    private final Setting<Boolean> echestholding = new Setting<>("EchestHolding", false);
    
    // ===== НАСТРОЙКИ ДЛЯ РЕЖИМА SOFIA =====
    private final Setting<Float> sofiaWalkSpeed = new Setting<>("SofiaWalkSpeed", 0.98f, 0.5f, 1.0f, v -> mode.is(Mode.Sofia));
    private final Setting<Float> sofiaStrafeAmount = new Setting<>("SofiaStrafe", 0.03f, 0.01f, 0.1f, v -> mode.is(Mode.Sofia));
    private final Setting<Boolean> sofiaAutoLockY = new Setting<>("SofiaAutoLockY", true, v -> mode.is(Mode.Sofia));
    private final Setting<Integer> sofiaKeepRotationTicks = new Setting<>("SofiaKeepRotation", 3, 1, 10, v -> mode.is(Mode.Sofia));
    
    private final Setting<SettingGroup> renderCategory = new Setting<>("Render", new SettingGroup(false, 0));
    private final Setting<Boolean> render = new Setting<>("Render", true).addToGroup(renderCategory);
    private final Setting<BlockAnimationUtility.BlockRenderMode> renderMode = new Setting<>("RenderMode", BlockAnimationUtility.BlockRenderMode.All).addToGroup(renderCategory);
    private final Setting<BlockAnimationUtility.BlockAnimationMode> animationMode = new Setting<>("AnimationMode", BlockAnimationUtility.BlockAnimationMode.Fade).addToGroup(renderCategory);
    private final Setting<ColorSetting> renderFillColor = new Setting<>("RenderFillColor", new ColorSetting(HudEditor.getColor(0))).addToGroup(renderCategory);
    private final Setting<ColorSetting> renderLineColor = new Setting<>("RenderLineColor", new ColorSetting(HudEditor.getColor(0))).addToGroup(renderCategory);
    private final Setting<Integer> renderLineWidth = new Setting<>("RenderLineWidth", 2, 1, 5).addToGroup(renderCategory);

    private enum Mode {
        NCP, StrictNCP, Grim, Sofia
    }

    private enum Switch {
        Normal, Silent, Inventory, None
    }

    private final Timer timer = new Timer();
    private BlockPosWithFacing currentblock;
    private int prevY;
    
    // ===== ПЕРЕМЕННЫЕ ДЛЯ SOFIA (серверные ротации) =====
    private float sofiaStrafeTimer = 0;
    private float sofiaStrafeDirection = 1;
    private float serverYaw = 0;
    private float serverPitch = 0;
    private int keepRotationTicks = 0;
    private boolean hasRotated = false;

    public Scaffold() {
        super("Scaffold", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        prevY = -999;
        sofiaStrafeTimer = 0;
        sofiaStrafeDirection = 1;
        keepRotationTicks = 0;
        hasRotated = false;
    }

    @EventHandler
    public void onMove(EventMove event) {
        if (fullNullCheck()) return;
        
        // ===== SOFIA РЕЖИМ: легитное движение =====
        if (mode.is(Mode.Sofia)) {
            double x = event.getX();
            double z = event.getZ();
            
            if (MovementUtility.isMoving()) {
                x *= sofiaWalkSpeed.getValue();
                z *= sofiaWalkSpeed.getValue();
            }
            
            // Легкое "шатание" в стороны (имитация ансофта)
            if (MovementUtility.isMoving() && mc.player.isOnGround()) {
                sofiaStrafeTimer += 0.02f;
                if (sofiaStrafeTimer > 1.0f) {
                    sofiaStrafeDirection *= -1;
                    sofiaStrafeTimer = 0;
                }
                
                float strafe = sofiaStrafeAmount.getValue() * sofiaStrafeDirection;
                double angle = Math.toRadians(mc.player.getYaw() + 90);
                x += Math.cos(angle) * strafe;
                z += Math.sin(angle) * strafe;
            }
            
            event.setX(x);
            event.setZ(z);
            event.cancel();
            return;
        }
        
        // ===== ОРИГИНАЛЬНЫЙ SAFEWALK =====
        if (safewalk.getValue() && !mode.is(Mode.Grim)) {
            double x = event.getX();
            double y = event.getY();
            double z = event.getZ();

            if (mc.player.isOnGround() && !mc.player.noClip) {
                double increment;
                for (increment = 0.05D; x != 0.0D && isOffsetBBEmpty(x, 0.0D); ) {
                    if (x < increment && x >= -increment) {
                        x = 0.0D;
                    } else if (x > 0.0D) {
                        x -= increment;
                    } else {
                        x += increment;
                    }
                }
                while (z != 0.0D && isOffsetBBEmpty(0.0D, z)) {
                    if (z < increment && z >= -increment) {
                        z = 0.0D;
                    } else if (z > 0.0D) {
                        z -= increment;
                    } else {
                        z += increment;
                    }
                }
                while (x != 0.0D && z != 0.0D && isOffsetBBEmpty(x, z)) {
                    if (x < increment && x >= -increment) {
                        x = 0.0D;
                    } else if (x > 0.0D) {
                        x -= increment;
                    } else {
                        x += increment;
                    }
                    if (z < increment && z >= -increment) {
                        z = 0.0D;
                    } else if (z > 0.0D) {
                        z -= increment;
                    } else {
                        z += increment;
                    }
                }
            }
            event.setX(x);
            event.setY(y);
            event.setZ(z);
            event.cancel();
        }
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mode.is(Mode.Grim) || mode.is(Mode.Sofia)) {
            preAction();
            postAction();
        }
        
        // Уменьшаем счетчик удержания ротации
        if (keepRotationTicks > 0) {
            keepRotationTicks--;
        }
    }

    @EventHandler
    public void onPre(EventSync e) {
        if (!mode.is(Mode.Grim) && !mode.is(Mode.Sofia))
            preAction();
    }

    public void preAction() {
        currentblock = null;

        if (mc.player.isSneaking() && !allowShift.getValue()) return;

        if (prePlace(false) == -1) return;

        if (mc.options.jumpKey.isPressed() && !MovementUtility.isMoving())
            prevY = (int) (Math.floor(mc.player.getY() - 1));

        if (MovementUtility.isMoving() && autoJump.getValue()) {
            if (mc.options.jumpKey.isPressed()) {
                if (onlyNotHoldingSpace.getValue())
                    prevY = (int) (Math.floor(mc.player.getY() - 1));
            } else if (mc.player.isOnGround())
                mc.player.jump();
        }

        BlockPos blockPos2;
        
        // Lock Y для Sofia
        if (mode.is(Mode.Sofia) && sofiaAutoLockY.getValue() && prevY != -999) {
            blockPos2 = BlockPos.ofFloored(mc.player.getX(), prevY, mc.player.getZ());
        } else if (lockY.getValue() && prevY != -999) {
            blockPos2 = BlockPos.ofFloored(mc.player.getX(), prevY, mc.player.getZ());
        } else {
            blockPos2 = new BlockPos((int) Math.floor(mc.player.getX()), (int) (Math.floor(mc.player.getY() - 1)), (int) Math.floor(mc.player.getZ()));
        }

        if (!mc.world.getBlockState(blockPos2).isReplaceable()) return;

        currentblock = checkNearBlocksExtended(blockPos2);
        
        // ===== SOFIA: только вычисляем ротации, НЕ меняем клиентскую камеру =====
        if (currentblock != null && rotate.getValue()) {
            Vec3d hitVec = new Vec3d(currentblock.position().getX() + 0.5, currentblock.position().getY() + 0.5, currentblock.position().getZ() + 0.5).add(new Vec3d(currentblock.facing().getUnitVector()).multiply(0.5));
            float[] rotations = InteractionUtility.calculateAngle(hitVec);
            
            if (mode.is(Mode.Sofia)) {
                // Сохраняем ротации для отправки в пакетах
                serverYaw = rotations[0];
                serverPitch = rotations[1];
                hasRotated = true;
                // keepRotationTicks устанавливается при отправке пакета
            } else {
                // Для других режимов меняем клиентскую камеру
                mc.player.setYaw(rotations[0]);
                mc.player.setPitch(rotations[1]);
            }
        }
    }

    @EventHandler
    public void onPost(EventPostSync e) {
        if (!mode.is(Mode.Grim) && !mode.is(Mode.Sofia))
            postAction();
    }

    public void postAction() {
        float offset = mode.is(Mode.Grim) || mode.is(Mode.Sofia) ? 0.3f : 0.2f;

        if (mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-offset, 0, -offset).offset(0, -0.5, 0)).iterator().hasNext())
            return;

        if (currentblock == null) return;

        int prevItem = prePlace(true);

        if (prevItem != -1) {
            if (mc.player.input.jumping && !MovementUtility.isMoving() && tower.getValue() && !mode.is(Mode.Grim) && !mode.is(Mode.Sofia)) {
                mc.player.setVelocity(0.0, 0.42, 0.0);
                if (timer.passedMs(1500)) {
                    mc.player.setVelocity(mc.player.getVelocity().x, -0.28, mc.player.getVelocity().z);
                    timer.reset();
                }
            } else timer.reset();

            BlockHitResult bhr;

            // Точная установка блока для Sofia
            if (mode.is(Mode.StrictNCP) || mode.is(Mode.Sofia)) {
                bhr = new BlockHitResult(new Vec3d(currentblock.position().getX() + 0.5, currentblock.position().getY() + 0.5, currentblock.position().getZ() + 0.5).add(new Vec3d(currentblock.facing().getUnitVector()).multiply(0.5)), currentblock.facing(), currentblock.position(), false);
            } else {
                bhr = new BlockHitResult(new Vec3d((double) currentblock.position().getX() + Math.random(), currentblock.position().getY() + 0.99f, (double) currentblock.position().getZ() + Math.random()), currentblock.facing(), currentblock.position(), false);
            }

            float[] rotations = InteractionUtility.calculateAngle(bhr.getPos());
            boolean useServerRotations = mode.is(Mode.Grim) || mode.is(Mode.Sofia);
            
            // Для Sofia используем сохраненные ротации
            float finalYaw = useServerRotations ? (hasRotated ? serverYaw : rotations[0]) : rotations[0];
            float finalPitch = useServerRotations ? (hasRotated ? serverPitch : rotations[1]) : rotations[1];
            
            // Устанавливаем keepRotationTicks для удержания ротации
            if (mode.is(Mode.Sofia) && hasRotated) {
                keepRotationTicks = sofiaKeepRotationTicks.getValue();
                hasRotated = false;
            }

            boolean sneak = InteractionUtility.needSneak(mc.world.getBlockState(bhr.getBlockPos()).getBlock()) && !mc.player.isSneaking();

            if (sneak)
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));

            // ===== КЛЮЧЕВОЕ: отправляем пакет с серверными ротациями =====
            if (useServerRotations) {
                // Отправляем пакет движения с нужными ротациями
                sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), finalYaw, finalPitch, mc.player.isOnGround()));
            }

            if (placeMode.getValue() == InteractionUtility.PlaceMode.Packet && !mode.is(Mode.Grim) && !mode.is(Mode.Sofia)) {
                boolean finalIsOffhand = prevItem == -2;
                sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(finalIsOffhand ? Hand.OFF_HAND : Hand.MAIN_HAND, bhr, id));
            } else {
                mc.interactionManager.interactBlock(mc.player, prevItem == -2 ? Hand.OFF_HAND : Hand.MAIN_HAND, bhr);
            }

            mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(prevItem == -2 ? Hand.OFF_HAND : Hand.MAIN_HAND));

            prevY = currentblock.position().getY();

            if (sneak)
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));

            // Отправляем второй пакет для восстановления ротации (если нужно)
            if (useServerRotations && keepRotationTicks > 0) {
                // Возвращаем клиентские ротации в следующем пакете
                sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()));
            }

            if (render.getValue())
                BlockAnimationUtility.renderBlock(currentblock.position(), renderLineColor.getValue().getColorObject(), renderLineWidth.getValue(), renderFillColor.getValue().getColorObject(), animationMode.getValue(), renderMode.getValue());

            postPlace(prevItem);
        }
    }

    private BlockPosWithFacing checkNearBlocksExtended(BlockPos blockPos) {
        BlockPosWithFacing ret = null;

        ret = checkNearBlocks(blockPos);
        if (ret != null) return ret;

        ret = checkNearBlocks(blockPos.add(-1, 0, 0));
        if (ret != null) return ret;

        ret = checkNearBlocks(blockPos.add(1, 0, 0));
        if (ret != null) return ret;

        ret = checkNearBlocks(blockPos.add(0, 0, 1));
        if (ret != null) return ret;

        ret = checkNearBlocks(blockPos.add(0, 0, -1));
        if (ret != null) return ret;

        ret = checkNearBlocks(blockPos.add(-2, 0, 0));
        if (ret != null) return ret;

        ret = checkNearBlocks(blockPos.add(2, 0, 0));
        if (ret != null) return ret;

        ret = checkNearBlocks(blockPos.add(0, 0, 2));
        if (ret != null) return ret;

        ret = checkNearBlocks(blockPos.add(0, 0, -2));
        if (ret != null) return ret;

        ret = checkNearBlocks(blockPos.add(0, -1, 0));
        if (ret != null) return ret;

        ret = checkNearBlocks(blockPos.add(1, -1, 0));
        if (ret != null) return ret;

        ret = checkNearBlocks(blockPos.add(-1, -1, 0));
        if (ret != null) return ret;

        ret = checkNearBlocks(blockPos.add(0, -1, 1));
        if (ret != null) return ret;

        return checkNearBlocks(blockPos.add(0, -1, -1));
    }

    private int prePlace(boolean swap) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null)
            return -1;

        if (mc.player.getOffHandStack().getItem() instanceof BlockItem bi && !bi.getBlock().getDefaultState().isReplaceable())
            return -2;

        if (mc.player.getMainHandStack().getItem() instanceof BlockItem bi && !bi.getBlock().getDefaultState().isReplaceable())
            return mc.player.getInventory().selectedSlot;

        int prevSlot = mc.player.getInventory().selectedSlot;

        SearchInvResult hotbarResult = InventoryUtility.findInHotBar(i -> i.getItem() instanceof BlockItem bi && !bi.getBlock().getDefaultState().isReplaceable());
        SearchInvResult invResult = InventoryUtility.findInInventory(i -> i.getItem() instanceof BlockItem bi && !bi.getBlock().getDefaultState().isReplaceable());

        if (swap)
            switch (autoSwitch.getValue()) {
                case Inventory -> {
                    if (invResult.found()) {
                        prevSlot = invResult.slot();
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, prevSlot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
                        sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                    }
                }
                case Normal, Silent -> hotbarResult.switchTo();
            }

        return prevSlot;
    }

    private void postPlace(int prevSlot) {
        if (prevSlot == -1 || prevSlot == -2)
            return;

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
