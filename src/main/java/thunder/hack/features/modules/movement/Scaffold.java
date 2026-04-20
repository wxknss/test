package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import thunder.hack.events.impl.*;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.client.HudEditor;
import thunder.hack.features.modules.client.Rotations;
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
    // Основные режимы
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.NCP);
    private final Setting<RotationMode> rotationMode = new Setting<>("RotationMode", RotationMode.Normal);
    private final Setting<MoveFixMode> moveFixMode = new Setting<>("MoveFixMode", MoveFixMode.None);
    
    // Настройки ротаций
    private final Setting<Boolean> silentRotations = new Setting<>("SilentRotations", true);
    private final Setting<Boolean> strictDown = new Setting<>("StrictDown", true, v -> rotationMode.is(RotationMode.Grim));
    private final Setting<Boolean> teleportRotations = new Setting<>("TeleportRotations", false);
    private final Setting<Boolean> sideWaysRotations = new Setting<>("SideWaysRotations", true);
    private final Setting<Boolean> jumpRotate = new Setting<>("JumpRotate", true);
    private final Setting<Float> rotationSpeed = new Setting<>("RotationSpeed", 180f, 30f, 180f);
    private final Setting<Boolean> randomizePitch = new Setting<>("RandomizePitch", true);
    
    // Настройки движения
    private final Setting<Boolean> sideMovement = new Setting<>("SideMovement", true);
    private final Setting<Boolean> sprintFix = new Setting<>("SprintFix", true);
    private final Setting<Boolean> airStrafe = new Setting<>("AirStrafe", true);
    
    // Основные настройки
    private final Setting<InteractionUtility.PlaceMode> placeMode = new Setting<>("PlaceMode", InteractionUtility.PlaceMode.Normal, v -> !mode.is(Mode.Grim));
    private final Setting<Switch> autoSwitch = new Setting<>("Switch", Switch.Silent);
    private final Setting<Boolean> lockY = new Setting<>("LockY", false);
    private final Setting<Boolean> autoJump = new Setting<>("AutoJump", false);
    private final Setting<Boolean> allowShift = new Setting<>("WorkWhileSneaking", false);
    private final Setting<Boolean> tower = new Setting<>("Tower", true, v -> !mode.is(Mode.Grim));
    private final Setting<Boolean> safewalk = new Setting<>("SafeWalk", true);
    private final Setting<Integer> blocksPerTick = new Setting<>("BlocksPerTick", 1, 1, 10);
    private final Setting<Integer> placeDelay = new Setting<>("PlaceDelay", 0, 0, 5);
    
    // Рендер
    private final Setting<SettingGroup> renderCategory = new Setting<>("Render", new SettingGroup(false, 0));
    private final Setting<Boolean> render = new Setting<>("Render", true).addToGroup(renderCategory);
    private final Setting<BlockAnimationUtility.BlockRenderMode> renderMode = new Setting<>("RenderMode", BlockAnimationUtility.BlockRenderMode.All).addToGroup(renderCategory);
    private final Setting<BlockAnimationUtility.BlockAnimationMode> animationMode = new Setting<>("AnimationMode", BlockAnimationUtility.BlockAnimationMode.Fade).addToGroup(renderCategory);
    private final Setting<ColorSetting> renderFillColor = new Setting<>("RenderFillColor", new ColorSetting(HudEditor.getColor(0))).addToGroup(renderCategory);
    private final Setting<ColorSetting> renderLineColor = new Setting<>("RenderLineColor", new ColorSetting(HudEditor.getColor(0))).addToGroup(renderCategory);
    private final Setting<Integer> renderLineWidth = new Setting<>("RenderLineWidth", 2, 1, 5).addToGroup(renderCategory);

    private enum Mode {
        NCP, StrictNCP, Grim, Matrix, Vulcan, Verus, AAC
    }
    
    private enum RotationMode {
        Normal,         // Обычные ротации
        Silent,         // Только на сервере
        Grim,           // Для Грим (строго вниз)
        Matrix,         // Для Матрикс
        Teleport,       // Телепорт ротации
        Legit,          // Легитные с лимитом скорости
        Reverse,        // Реверс ротации (отворачивание)
        Dynamic         // Динамические в зависимости от стороны
    }
    
    private enum MoveFixMode {
        None,           // Без фикса
        Strafe,         // Strafe фикс
        Silent,         // Сайлент фикс
        Reverse,        // Реверс фикс
        Velocity,       // Через velocity
        Matrix,         // Матрикс фикс
        Grim,           // Грим фикс
        Teleport        // Телепорт фикс
    }

    private enum Switch {
        Normal, Silent, Inventory, None
    }

    private final Timer timer = new Timer();
    private final Timer placeTimer = new Timer();
    private BlockPosWithFacing currentblock;
    private int prevY;
    private int blocksPlacedThisTick = 0;
    
    // Для ротаций
    private float targetYaw, targetPitch;
    private float lastYaw, lastPitch;
    private float serverYaw, serverPitch;
    private boolean rotating = false;
    private Direction lastDirection = Direction.NORTH;
    
    // Для телепорт режима
    private boolean teleported = false;
    private Vec3d teleportPos;

    public Scaffold() {
        super("Scaffold", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        prevY = -999;
        blocksPlacedThisTick = 0;
        rotating = false;
        teleported = false;
        lastYaw = mc.player.getYaw();
        lastPitch = mc.player.getPitch();
    }

    @EventHandler
    public void onMove(EventMove event) {
        if (fullNullCheck()) return;
        
        // SafeWalk
        if (safewalk.getValue()) {
            handleSafeWalk(event);
        }
        
        // MoveFix
        if (moveFixMode.getValue() != MoveFixMode.None) {
            handleMoveFix(event);
        }
        
        // Side Movement
        if (sideMovement.getValue() && currentblock != null) {
            handleSideMovement(event);
        }
    }
    
    private void handleMoveFix(EventMove event) {
        if (currentblock == null) return;
        
        float targetRot = getTargetRotation();
        
        switch (moveFixMode.getValue()) {
            case Strafe:
                // Strafe fix для NCP
                if (MovementUtility.isMoving()) {
                    float forward = mc.player.input.movementForward;
                    float strafe = mc.player.input.movementSideways;
                    float yaw = targetRot;
                    
                    double motionX = forward * 0.98 + strafe * 0.98;
                    double motionZ = forward * 0.98 - strafe * 0.98;
                    
                    float cos = (float) Math.cos(Math.toRadians(yaw + 90));
                    float sin = (float) Math.sin(Math.toRadians(yaw + 90));
                    
                    event.setX(motionX * cos - motionZ * sin);
                    event.setZ(motionZ * cos + motionX * sin);
                }
                break;
                
            case Silent:
                // Сайлент фикс
                Rotations mod = (Rotations) ModuleManager.getModule(Rotations.class);
                if (mod != null) {
                    mod.fixRotation = targetRot;
                }
                break;
                
            case Reverse:
                // Реверс фикс - обратное направление
                if (MovementUtility.isMoving()) {
                    float yawDiff = targetRot - mc.player.getYaw();
                    event.setX(-event.getX());
                    event.setZ(-event.getZ());
                }
                break;
                
            case Matrix:
                // Матрикс фикс с предсказанием
                handleMatrixMoveFix(event);
                break;
                
            case Grim:
                // Грим фикс - без изменения velocity
                if (sprintFix.getValue() && mc.player.isSprinting()) {
                    mc.player.setSprinting(false);
                }
                break;
                
            case Teleport:
                // Телепорт фикс
                handleTeleportMoveFix(event);
                break;
        }
    }
    
    private void handleMatrixMoveFix(EventMove event) {
        // Специальный фикс для Матрикс 7
        if (mc.player.isOnGround()) {
            float yawDiff = targetYaw - mc.player.getYaw();
            
            if (Math.abs(yawDiff) > 90) {
                // Инвертируем движение при большом повороте
                event.setX(-event.getX() * 0.98);
                event.setZ(-event.getZ() * 0.98);
                mc.player.setSprinting(false);
            } else if (Math.abs(yawDiff) > 45) {
                // Уменьшаем скорость
                event.setX(event.getX() * 0.85);
                event.setZ(event.getZ() * 0.85);
            }
        }
    }
    
    private void handleTeleportMoveFix(EventMove event) {
        if (!teleported || teleportPos == null) return;
        
        // Компенсируем движение после телепорта
        double deltaX = mc.player.getX() - teleportPos.x;
        double deltaZ = mc.player.getZ() - teleportPos.z;
        
        event.setX(event.getX() - deltaX);
        event.setZ(event.getZ() - deltaZ);
    }
    
    private void handleSideMovement(EventMove event) {
        if (!MovementUtility.isMoving()) return;
        
        // Позволяет двигаться вбок во время скэффолда
        float strafe = mc.player.input.movementSideways;
        if (strafe != 0) {
            float yaw = getTargetRotation();
            float sin = (float) Math.sin(Math.toRadians(yaw));
            float cos = (float) Math.cos(Math.toRadians(yaw));
            
            double sideMotion = strafe * 0.2;
            event.setX(event.getX() + sideMotion * cos);
            event.setZ(event.getZ() + sideMotion * sin);
        }
    }
    
    private void handleSafeWalk(EventMove event) {
        double x = event.getX();
        double y = event.getY();
        double z = event.getZ();

        if (mc.player.isOnGround() && !mc.player.noClip) {
            double increment = 0.05D;
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
        event.setX(x);
        event.setZ(z);
        event.cancel();
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mode.is(Mode.Grim) || mode.is(Mode.Matrix)) {
            preAction();
            postAction();
        }
        blocksPlacedThisTick = 0;
    }

    @EventHandler
    public void onPre(EventSync e) {
        if (!mode.is(Mode.Grim) && !mode.is(Mode.Matrix))
            preAction();
            
        // Обработка ротаций до синхронизации
        if (currentblock != null && silentRotations.getValue()) {
            handleRotations(e);
        }
    }
    
    @EventHandler
    public void onPostSync(EventPostSync e) {
        if (rotating) {
            // Возвращаем ротации
            mc.player.setYaw(lastYaw);
            mc.player.setPitch(lastPitch);
            rotating = false;
        }
    }
    
    private void handleRotations(EventSync e) {
        if (currentblock == null) return;
        
        lastYaw = mc.player.getYaw();
        lastPitch = mc.player.getPitch();
        
        calculateRotations();
        
        switch (rotationMode.getValue()) {
            case Normal:
                mc.player.setYaw(targetYaw);
                mc.player.setPitch(targetPitch);
                rotating = true;
                break;
                
            case Silent:
                // Только на сервере через пакеты
                sendRotationPacket(targetYaw, targetPitch);
                rotating = false;
                break;
                
            case Grim:
                handleGrimRotations();
                break;
                
            case Matrix:
                handleMatrixRotations();
                break;
                
            case Teleport:
                handleTeleportRotations();
                break;
                
            case Legit:
                handleLegitRotations();
                break;
                
            case Reverse:
                handleReverseRotations();
                break;
                
            case Dynamic:
                handleDynamicRotations();
                break;
        }
    }
    
    private void calculateRotations() {
        if (currentblock == null) return;
        
        Vec3d hitVec = getHitVector();
        
        // Базовая ротация
        float[] rots = InteractionUtility.calculateAngle(hitVec);
        targetYaw = rots[0];
        targetPitch = rots[1];
        
        // Модификации для разных режимов
        switch (mode.getValue()) {
            case Grim:
                targetPitch = strictDown.getValue() ? 90f : 85f + (randomizePitch.getValue() ? (float) (Math.random() * 5) : 0);
                break;
            case Matrix:
                targetPitch = 82f + (float) (Math.random() * 8);
                break;
            case Vulcan:
                targetPitch = 75f;
                targetYaw += (float) (Math.random() * 10 - 5);
                break;
        }
        
        // Боковые ротации
        if (sideWaysRotations.getValue()) {
            Direction facing = currentblock.facing();
            if (facing == Direction.EAST) targetYaw += 5;
            else if (facing == Direction.WEST) targetYaw -= 5;
            else if (facing == Direction.NORTH) targetYaw = 180;
            else if (facing == Direction.SOUTH) targetYaw = 0;
        }
    }
    
    private Vec3d getHitVector() {
        BlockPos pos = currentblock.position();
        Direction facing = currentblock.facing();
        
        switch (mode.getValue()) {
            case Grim:
                // Строго в центр блока вниз
                return new Vec3d(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                
            case Matrix:
                // Немного рандома для Матрикс
                return new Vec3d(
                    pos.getX() + 0.5 + (Math.random() * 0.1 - 0.05),
                    pos.getY() + 0.5 + (Math.random() * 0.1),
                    pos.getZ() + 0.5 + (Math.random() * 0.1 - 0.05)
                );
                
            case StrictNCP:
                return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    .add(new Vec3d(facing.getUnitVector()).multiply(0.5));
                    
            default:
                return new Vec3d(
                    pos.getX() + Math.random(),
                    pos.getY() + 0.99f,
                    pos.getZ() + Math.random()
                );
        }
    }
    
    private void handleGrimRotations() {
        // Грим требует строго вниз на блок
        targetPitch = 90f;
        targetYaw = lastYaw; // Не меняем yaw
        
        // Отправляем через пакет
        sendRotationPacket(targetYaw, targetPitch);
        
        // Для прыжков отворачиваем
        if (jumpRotate.getValue() && mc.options.jumpKey.isPressed()) {
            targetPitch = -90f;
            sendRotationPacket(targetYaw, targetPitch);
        }
    }
    
    private void handleMatrixRotations() {
        // Матрикс 7 байпас
        if (mc.player.isOnGround()) {
            targetPitch = 85f + (float) (Math.random() * 5);
        } else {
            targetPitch = 80f;
        }
        
        // Плавное изменение
        targetYaw = smoothRotation(lastYaw, targetYaw, rotationSpeed.getValue());
        
        sendRotationPacket(targetYaw, targetPitch);
    }
    
    private void handleTeleportRotations() {
        if (!teleported) {
            // Сохраняем позицию для телепорта
            teleportPos = mc.player.getPos();
            
            // Телепортируем ротацию мгновенно
            sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                targetYaw, targetPitch, mc.player.isOnGround()
            ));
            
            teleported = true;
        } else {
            teleported = false;
        }
    }
    
    private void handleLegitRotations() {
        // Легитные ротации с лимитом скорости
        targetYaw = smoothRotation(lastYaw, targetYaw, rotationSpeed.getValue());
        targetPitch = smoothRotation(lastPitch, targetPitch, rotationSpeed.getValue() / 2);
        
        mc.player.setYaw(targetYaw);
        mc.player.setPitch(targetPitch);
        rotating = true;
    }
    
    private void handleReverseRotations() {
        // Реверс ротации - смотрит в противоположную сторону
        targetYaw = lastYaw + 180;
        targetPitch = -targetPitch;
        
        if (silentRotations.getValue()) {
            sendRotationPacket(targetYaw, targetPitch);
        } else {
            mc.player.setYaw(targetYaw);
            mc.player.setPitch(targetPitch);
            rotating = true;
        }
    }
    
    private void handleDynamicRotations() {
        // Динамические ротации в зависимости от стороны блока
        Direction facing = currentblock.facing();
        
        switch (facing) {
            case NORTH:
                targetYaw = 180;
                targetPitch = 80;
                break;
            case SOUTH:
                targetYaw = 0;
                targetPitch = 80;
                break;
            case EAST:
                targetYaw = -90;
                targetPitch = 85;
                break;
            case WEST:
                targetYaw = 90;
                targetPitch = 85;
                break;
            case UP:
                targetPitch = 90;
                break;
            case DOWN:
                targetPitch = -90;
                break;
        }
        
        sendRotationPacket(targetYaw, targetPitch);
    }
    
    private float smoothRotation(float from, float to, float speed) {
        float diff = to - from;
        if (diff > 180) diff -= 360;
        if (diff < -180) diff += 360;
        
        if (Math.abs(diff) > speed) {
            return from + (diff > 0 ? speed : -speed);
        }
        return to;
    }
    
    private float getTargetRotation() {
        if (currentblock != null) {
            Direction facing = currentblock.facing();
            switch (facing) {
                case NORTH: return 180;
                case SOUTH: return 0;
                case EAST: return -90;
                case WEST: return 90;
                default: return lastYaw;
            }
        }
        return lastYaw;
    }
    
    private void sendRotationPacket(float yaw, float pitch) {
        if (mode.is(Mode.Grim) || mode.is(Mode.Matrix)) {
            sendPacket(new PlayerMoveC2SPacket.Full(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                yaw, pitch, mc.player.isOnGround()
            ));
        } else {
            sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround()));
        }
    }

    public void preAction() {
        currentblock = null;

        if (mc.player.isSneaking() && !allowShift.getValue()) return;

        if (prePlace(false) == -1) return;

        if (mc.options.jumpKey.isPressed() && !MovementUtility.isMoving())
            prevY = (int) (Math.floor(mc.player.getY() - 1));

        if (MovementUtility.isMoving() && autoJump.getValue()) {
            if (!mc.options.jumpKey.isPressed() && mc.player.isOnGround())
                mc.player.jump();
        }

        BlockPos blockPos2 = lockY.getValue() && prevY != -999 ?
                BlockPos.ofFloored(mc.player.getX(), prevY, mc.player.getZ())
                : new BlockPos((int) Math.floor(mc.player.getX()), (int) (Math.floor(mc.player.getY() - 1)), (int) Math.floor(mc.player.getZ()));

        if (!mc.world.getBlockState(blockPos2).isReplaceable()) return;

        currentblock = checkNearBlocksExtended(blockPos2);
    }

    public void postAction() {
        if (!placeTimer.passedMs(placeDelay.getValue())) return;
        
        float offset = mode.is(Mode.Grim) ? 0.3f : 0.2f;

        if (mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-offset, 0, -offset).offset(0, -0.5, 0)).iterator().hasNext())
            return;

        if (currentblock == null) return;

        int blocksToPlace = blocksPerTick.getValue();
        
        for (int i = 0; i < blocksToPlace; i++) {
            if (blocksPlacedThisTick >= blocksPerTick.getValue()) break;
            if (currentblock == null) break;
            
            int prevItem = prePlace(true);

            if (prevItem != -1) {
                handleTower();
                
                BlockHitResult bhr = createBlockHitResult();
                
                boolean sneak = InteractionUtility.needSneak(mc.world.getBlockState(bhr.getBlockPos()).getBlock()) && !mc.player.isSneaking();

                if (sneak)
                    sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));

                // Отправляем ротации для пакетных режимов
                if (mode.is(Mode.Grim) || mode.is(Mode.Matrix) || rotationMode.is(RotationMode.Silent)) {
                    float[] rots = InteractionUtility.calculateAngle(bhr.getPos());
                    sendRotationPacket(rots[0], mode.is(Mode.Grim) ? 90 : rots[1]);
                }

                if (placeMode.getValue() == InteractionUtility.PlaceMode.Packet && !mode.is(Mode.Grim)) {
                    boolean finalIsOffhand = prevItem == -2;
                    sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(finalIsOffhand ? Hand.OFF_HAND : Hand.MAIN_HAND, bhr, id));
                } else {
                    mc.interactionManager.interactBlock(mc.player, prevItem == -2 ? Hand.OFF_HAND : Hand.MAIN_HAND, bhr);
                }

                sendPacket(new HandSwingC2SPacket(prevItem == -2 ? Hand.OFF_HAND : Hand.MAIN_HAND));

                prevY = currentblock.position().getY();

                if (sneak)
                    sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));

                if (render.getValue())
                    BlockAnimationUtility.renderBlock(currentblock.position(), renderLineColor.getValue().getColorObject(), renderLineWidth.getValue(), renderFillColor.getValue().getColorObject(), animationMode.getValue(), renderMode.getValue());

                postPlace(prevItem);
                
                blocksPlacedThisTick++;
                placeTimer.reset();
                
                if (i < blocksToPlace - 1) {
                    findNextBlock();
                }
            }
        }
    }
    
    private void handleTower() {
        if (mc.player.input.jumping && !MovementUtility.isMoving() && tower.getValue() && !mode.is(Mode.Grim)) {
            mc.player.setVelocity(0.0, 0.42, 0.0);
            if (timer.passedMs(1500)) {
                mc.player.setVelocity(mc.player.getVelocity().x, -0.28, mc.player.getVelocity().z);
                timer.reset();
            }
        } else {
            timer.reset();
        }
    }
    
    private BlockHitResult createBlockHitResult() {
        if (mode.is(Mode.StrictNCP) || mode.is(Mode.Grim)) {
            return new BlockHitResult(
                new Vec3d(currentblock.position().getX() + 0.5, currentblock.position().getY() + 0.5, currentblock.position().getZ() + 0.5)
                    .add(new Vec3d(currentblock.facing().getUnitVector()).multiply(0.5)), 
                currentblock.facing(), currentblock.position(), false
            );
        } else {
            return new BlockHitResult(
                new Vec3d(
                    currentblock.position().getX() + (mode.is(Mode.Matrix) ? 0.5 : Math.random()),
                    currentblock.position().getY() + 0.99f,
                    currentblock.position().getZ() + (mode.is(Mode.Matrix) ? 0.5 : Math.random())
                ), 
                currentblock.facing(), currentblock.position(), false
            );
        }
    }
    
    private void findNextBlock() {
        BlockPos blockPos2 = lockY.getValue() && prevY != -999 ?
                BlockPos.ofFloored(mc.player.getX(), prevY, mc.player.getZ())
                : new BlockPos((int) Math.floor(mc.player.getX()), (int) (Math.floor(mc.player.getY() - 1)), (int) Math.floor(mc.player.getZ()));
        
        if (mc.world.getBlockState(blockPos2).isReplaceable()) {
            currentblock = checkNearBlocksExtended(blockPos2);
        } else {
            currentblock = null;
        }
    }

    private BlockPosWithFacing checkNearBlocksExtended(BlockPos blockPos) {
        // Проверяем все возможные позиции
        BlockPosWithFacing ret = checkNearBlocks(blockPos);
        if (ret != null) return ret;
        
        // Проверяем вокруг
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) continue;
                ret = checkNearBlocks(blockPos.add(x, 0, z));
                if (ret != null) return ret;
            }
        }
        
        // Проверяем снизу
        for (int y = -1; y <= 0; y++) {
            ret = checkNearBlocks(blockPos.add(0, y, 0));
            if (ret != null) return ret;
            
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) continue;
                    ret = checkNearBlocks(blockPos.add(x, y, z));
                    if (ret != null) return ret;
                }
            }
        }
        
        return null;
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
