package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.EventSync;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.injection.accesors.IMinecraftClient;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.movement.Blink;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.Bind;
import thunder.hack.setting.impl.BooleanSettingGroup;
import thunder.hack.setting.impl.SettingGroup;
import thunder.hack.utility.Timer;
import thunder.hack.utility.world.ExplosionUtility;
import thunder.hack.utility.math.PredictUtility;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.SearchInvResult;

public final class AutoTotem extends Module {
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Matrix);
    private final Setting<PlaceMode> placeMode = new Setting<>("PlaceMode", PlaceMode.Smart);
    private final Setting<OffHand> offhand = new Setting<>("Item", OffHand.Totem);
    private final Setting<BooleanSettingGroup> bindSwap = new Setting<>("BindSwap", new BooleanSettingGroup(false), v -> offhand.is(OffHand.Totem));
    private final Setting<Bind> swapButton = new Setting<>("SwapButton", new Bind(GLFW.GLFW_KEY_CAPS_LOCK, false, false)).addToGroup(bindSwap);
    private final Setting<Swap> swapMode = new Setting<>("Swap", Swap.GappleShield).addToGroup(bindSwap);
    private final Setting<Boolean> ncpStrict = new Setting<>("NCPStrict", false);
    private final Setting<Float> healthF = new Setting<>("HP", 16f, 0f, 36f);
    private final Setting<Float> healthS = new Setting<>("ShieldGappleHp", 16f, 0f, 20f, v -> offhand.getValue() == OffHand.Shield);
    private final Setting<Boolean> calcAbsorption = new Setting<>("CalcAbsorption", true);
    private final Setting<Boolean> stopMotion = new Setting<>("StopMotion", false);
    private final Setting<Boolean> resetAttackCooldown = new Setting<>("ResetAttackCooldown", false);
    private final Setting<SettingGroup> safety = new Setting<>("Safety", new SettingGroup(false, 0));
    private final Setting<Boolean> hotbarFallBack = new Setting<>("HotbarFallback", false).addToGroup(safety);
    private final Setting<Boolean> fallBackCalc = new Setting<>("FallBackCalc", true, v -> hotbarFallBack.getValue()).addToGroup(safety);
    private final Setting<Boolean> onElytra = new Setting<>("OnElytra", true).addToGroup(safety);
    private final Setting<Boolean> onFall = new Setting<>("OnFall", true).addToGroup(safety);
    private final Setting<Boolean> onCrystal = new Setting<>("OnCrystal", true).addToGroup(safety);
    private final Setting<Boolean> onObsidianPlace = new Setting<>("OnObsidianPlace", false).addToGroup(safety);
    private final Setting<Boolean> onCrystalInHand = new Setting<>("OnCrystalInHand", false).addToGroup(safety);
    private final Setting<Boolean> onMinecartTnt = new Setting<>("OnMinecartTNT", true).addToGroup(safety);
    private final Setting<Boolean> onCreeper = new Setting<>("OnCreeper", true).addToGroup(safety);
    private final Setting<Boolean> onAnchor = new Setting<>("OnAnchor", true).addToGroup(safety);
    private final Setting<Boolean> onTnt = new Setting<>("OnTNT", true).addToGroup(safety);
    public final Setting<RCGap> rcGap = new Setting<>("RightClickGapple", RCGap.Off);
    private final Setting<Boolean> crappleSpoof = new Setting<>("CrappleSpoof", true, v -> offhand.getValue() == OffHand.GApple);
    
    // НОВЫЕ УМНЫЕ РЕЖИМЫ
    private final Setting<Integer> retryDelay = new Setting<>("RetryDelay", 50, 10, 200);
    private final Setting<Integer> freeSlot = new Setting<>("FreeSlot", 8, 0, 8);
    private final Setting<Boolean> keepFreeSlot = new Setting<>("KeepFreeSlot", true);
    private final Setting<Boolean> silentSwap = new Setting<>("SilentSwap", true);
    private final Setting<Boolean> offhandOnly = new Setting<>("OffhandOnly", false);
    private final Setting<Boolean> fallbackToHotbar = new Setting<>("FallbackToHotbar", true);
    private final Setting<Boolean> matrixInstant = new Setting<>("MatrixInstant", true);
    private final Setting<Integer> matrixDelay = new Setting<>("MatrixDelay", 1, 0, 10, v -> matrixInstant.getValue());

    private enum OffHand {Totem, Crystal, GApple, Shield}
    private enum Mode {Default, Alternative, Matrix, MatrixPick, NewVersion}
    private enum PlaceMode {Normal, Smart, Aggressive, MatrixOptimized, Silent, Instant}
    private enum Swap {GappleShield, BallShield, GappleBall, BallTotem}
    public enum RCGap {Off, Always, OnlySafe}

    private int delay;
    private int matrixRetry = 0;
    private Timer bindDelay = new Timer();
    private Timer retryTimer = new Timer();
    private Item prevItem;
    private int lastSlot = -1;

    public AutoTotem() {
        super("AutoTotem", Category.COMBAT);
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (matrixInstant.getValue() && placeMode.is(PlaceMode.MatrixOptimized)) {
            matrixRetry++;
            if (matrixRetry < matrixDelay.getValue()) {
                return;
            }
            matrixRetry = 0;
        }
        
        if (keepFreeSlot.getValue()) {
            reserveFreeSlot();
        }
        
        int slot = getItemSlot();
        
        if (slot != -1 && placeMode.is(PlaceMode.Aggressive)) {
            swapToAggressive(slot);
        } else if (slot != -1 && placeMode.is(PlaceMode.Silent)) {
            swapToSilent(slot);
        } else if (slot != -1 && placeMode.is(PlaceMode.Instant)) {
            swapToInstant(slot);
        } else if (slot != -1 && placeMode.is(PlaceMode.MatrixOptimized)) {
            swapToMatrixOptimized(slot);
        } else {
            swapTo(slot);
        }

        if (rcGap.not(RCGap.Off) && (mc.player.getMainHandStack().getItem() instanceof SwordItem) && mc.options.useKey.isPressed() && !mc.player.isUsingItem())
            ((IMinecraftClient) mc).idoItemUse();

        delay--;
    }
    
    private void reserveFreeSlot() {
        int currentSlot = mc.player.getInventory().selectedSlot;
        if (currentSlot != freeSlot.getValue()) {
            ItemStack stack = mc.player.getInventory().getStack(freeSlot.getValue());
            if (stack.isEmpty() || stack.getItem() == Items.AIR) {
                return;
            }
            for (int i = 0; i < 9; i++) {
                if (i != freeSlot.getValue() && mc.player.getInventory().getStack(i).isEmpty()) {
                    swapSlots(i, freeSlot.getValue());
                    break;
                }
            }
        }
    }
    
    private void swapSlots(int from, int to) {
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            from < 9 ? from + 36 : from,
            to < 9 ? to + 36 : to,
            SlotActionType.SWAP,
            mc.player
        );
        sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
    }
    
    private void swapToAggressive(int slot) {
        if (slot != -1 && delay <= 0 && retryTimer.passedMs(retryDelay.getValue())) {
            if (mc.currentScreen instanceof GenericContainerScreen) return;
            
            int prevSlot = mc.player.getInventory().selectedSlot;
            
            if (slot >= 9) {
                for (int i = 0; i < 3; i++) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, prevSlot, SlotActionType.SWAP, mc.player);
                    sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
                    mc.player.getInventory().selectedSlot = prevSlot;
                    sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                }
            } else {
                sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                mc.player.getInventory().selectedSlot = slot;
                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
                mc.player.getInventory().selectedSlot = prevSlot;
            }
            
            retryTimer.reset();
            delay = 2;
        }
    }
    
    private void swapToSilent(int slot) {
        if (slot != -1 && delay <= 0) {
            if (mc.currentScreen instanceof GenericContainerScreen) return;
            
            int prevSlot = mc.player.getInventory().selectedSlot;
            
            if (slot >= 9) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, prevSlot, SlotActionType.SWAP, mc.player);
            } else {
                sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                mc.player.getInventory().selectedSlot = slot;
                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
                mc.player.getInventory().selectedSlot = prevSlot;
            }
            
            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            delay = 1;
        }
    }
    
    private void swapToInstant(int slot) {
        if (slot != -1 && delay <= 0) {
            if (mc.currentScreen instanceof GenericContainerScreen) return;
            
            if (slot >= 9) {
                sendPacket(new PickFromInventoryC2SPacket(slot));
                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            } else {
                sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
            }
            
            delay = 0;
        }
    }
    
    private void swapToMatrixOptimized(int slot) {
        if (slot != -1 && delay <= 0) {
            if (mc.currentScreen instanceof GenericContainerScreen) return;
            
            int prevSlot = mc.player.getInventory().selectedSlot;
            int nearestSlot = findNearestCurrentItem();
            
            if (slot >= 9) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, nearestSlot, SlotActionType.SWAP, mc.player);
                sendPacket(new UpdateSelectedSlotC2SPacket(nearestSlot));
                mc.player.getInventory().selectedSlot = nearestSlot;
                
                ItemStack itemstack = mc.player.getOffHandStack();
                mc.player.setStackInHand(Hand.OFF_HAND, mc.player.getMainHandStack());
                mc.player.setStackInHand(Hand.MAIN_HAND, itemstack);
                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                
                sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
                mc.player.getInventory().selectedSlot = prevSlot;
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, nearestSlot, SlotActionType.SWAP, mc.player);
            } else {
                sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                mc.player.getInventory().selectedSlot = slot;
                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
                mc.player.getInventory().selectedSlot = prevSlot;
            }
            
            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            delay = 1;
        }
    }

    public void swapTo(int slot) {
        if (slot != -1 && delay <= 0 && retryTimer.passedMs(retryDelay.getValue())) {
            if (mc.currentScreen instanceof GenericContainerScreen) return;

            if (stopMotion.getValue()) mc.player.setVelocity(0, mc.player.getVelocity().getY(), 0);

            int nearestSlot = findNearestCurrentItem();
            int prevCurrentItem = mc.player.getInventory().selectedSlot;
            
            if (slot >= 9) {
                switch (mode.getValue()) {
                    case Default -> {
                        if (ncpStrict.getValue())
                            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                        clickSlotMethod(slot);
                        clickSlotMethod(45);
                        clickSlotMethod(slot);
                        sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                    }
                    case Alternative -> {
                        if (ncpStrict.getValue())
                            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                        clickSlotMethod(slot, nearestSlot, SlotActionType.SWAP);
                        clickSlotMethod(45, nearestSlot, SlotActionType.SWAP);
                        clickSlotMethod(slot, nearestSlot, SlotActionType.SWAP);
                        sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                    }
                    case Matrix -> {
                        if (ncpStrict.getValue())
                            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));

                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, nearestSlot, SlotActionType.SWAP, mc.player);
                        sendMessage("§7[AutoTotem] " + slot + " " + nearestSlot);

                        sendPacket(new UpdateSelectedSlotC2SPacket(nearestSlot));
                        mc.player.getInventory().selectedSlot = nearestSlot;

                        ItemStack itemstack = mc.player.getOffHandStack();
                        mc.player.setStackInHand(Hand.OFF_HAND, mc.player.getMainHandStack());
                        mc.player.setStackInHand(Hand.MAIN_HAND, itemstack);
                        sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));

                        sendPacket(new UpdateSelectedSlotC2SPacket(prevCurrentItem));
                        mc.player.getInventory().selectedSlot = prevCurrentItem;

                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, nearestSlot, SlotActionType.SWAP, mc.player);

                        sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                        if (resetAttackCooldown.getValue())
                            mc.player.resetLastAttackedTicks();
                    }
                    case MatrixPick -> {
                        sendMessage("§7[AutoTotem] " + slot + " pick");
                        sendPacket(new PickFromInventoryC2SPacket(slot));
                        sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                        int prevSlot = mc.player.getInventory().selectedSlot;
                        Managers.ASYNC.run(() -> mc.player.getInventory().selectedSlot = prevSlot, 300);
                    }
                    case NewVersion -> {
                        sendMessage("§7[AutoTotem] " + slot + " swap");
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
                        sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                    }
                }
            } else {
                sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                mc.player.getInventory().selectedSlot = slot;
                sendMessage("§7[AutoTotem] " + slot + " select");
                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                sendPacket(new UpdateSelectedSlotC2SPacket(prevCurrentItem));
                mc.player.getInventory().selectedSlot = prevCurrentItem;
                if (resetAttackCooldown.getValue())
                    mc.player.resetLastAttackedTicks();
            }
            retryTimer.reset();
            delay = (int) (2 + (Managers.SERVER.getPing() / 25f));
        }
    }
    
    private void clickSlotMethod(int slot) {
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }
    
    private void clickSlotMethod(int slot, int button, SlotActionType action) {
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, button, action, mc.player);
    }

    public static int findNearestCurrentItem() {
        int i = mc.player.getInventory().selectedSlot;
        if (i == 8) return 7;
        if (i == 0) return 1;
        return i - 1;
    }

    public int getItemSlot() {
        if (mc.player == null || mc.world == null) return -1;

        SearchInvResult gapple = InventoryUtility.findItemInInventory(Items.ENCHANTED_GOLDEN_APPLE);
        SearchInvResult crapple = InventoryUtility.findItemInInventory(Items.GOLDEN_APPLE);
        SearchInvResult shield = InventoryUtility.findItemInInventory(Items.SHIELD);
        Item offHandItem = mc.player.getOffHandStack().getItem();

        int itemSlot = -1;
        Item item = null;
        
        switch (offhand.getValue()) {
            case Totem -> {
                if (offHandItem != Items.TOTEM_OF_UNDYING && !mc.player.getOffHandStack().isEmpty())
                    prevItem = offHandItem;

                item = prevItem;

                if (bindSwap.getValue().isEnabled())
                    if (isKeyPressed(swapButton) && bindDelay.every(250)) {
                        switch (swapMode.getValue()) {
                            case BallShield -> {
                                if (mc.player.getOffHandStack().isEmpty() || offHandItem == Items.SHIELD)
                                    item = Items.PLAYER_HEAD;
                                else item = Items.SHIELD;
                            }
                            case GappleBall -> {
                                if (mc.player.getOffHandStack().isEmpty() || offHandItem == Items.GOLDEN_APPLE)
                                    item = Items.PLAYER_HEAD;
                                else item = Items.GOLDEN_APPLE;
                            }
                            case GappleShield -> {
                                if (mc.player.getOffHandStack().isEmpty() || offHandItem == Items.SHIELD)
                                    item = Items.GOLDEN_APPLE;
                                else item = Items.SHIELD;
                            }
                            case BallTotem -> {
                                if (mc.player.getOffHandStack().isEmpty() || offHandItem == Items.TOTEM_OF_UNDYING)
                                    item = Items.PLAYER_HEAD;
                                else item = Items.TOTEM_OF_UNDYING;
                            }
                        }
                        prevItem = item;
                    }
            }
            case Crystal -> item = Items.END_CRYSTAL;
            case GApple -> {
                if (crappleSpoof.getValue()) {
                    if (mc.player.hasStatusEffect(StatusEffects.ABSORPTION) && mc.player.getStatusEffect(StatusEffects.ABSORPTION).getAmplifier() > 2) {
                        if (crapple.found() || offHandItem == Items.GOLDEN_APPLE)
                            item = Items.GOLDEN_APPLE;
                        else if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE)
                            item = Items.ENCHANTED_GOLDEN_APPLE;
                    } else {
                        if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE)
                            item = Items.ENCHANTED_GOLDEN_APPLE;
                        else if (crapple.found() || offHandItem == Items.GOLDEN_APPLE)
                            item = Items.GOLDEN_APPLE;
                    }
                } else {
                    if (crapple.found() || offHandItem == Items.GOLDEN_APPLE)
                        item = Items.GOLDEN_APPLE;
                    else if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE)
                        item = Items.ENCHANTED_GOLDEN_APPLE;
                }
            }
            case Shield -> {
                if (shield.found() || offHandItem == Items.SHIELD) {
                    if (getTriggerHealth() <= healthS.getValue()) {
                        if (crapple.found() || offHandItem == Items.GOLDEN_APPLE)
                            item = Items.GOLDEN_APPLE;
                        else if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE)
                            item = Items.ENCHANTED_GOLDEN_APPLE;
                    } else {
                        if (!mc.player.getItemCooldownManager().isCoolingDown(Items.SHIELD)) item = Items.SHIELD;
                        else {
                            if (crapple.found() || offHandItem == Items.GOLDEN_APPLE)
                                item = Items.GOLDEN_APPLE;
                            else if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE)
                                item = Items.ENCHANTED_GOLDEN_APPLE;
                        }
                    }
                } else if (crapple.found() || offHandItem == Items.GOLDEN_APPLE)
                    item = Items.GOLDEN_APPLE;
            }
        }

        if (getTriggerHealth() <= healthF.getValue() && (InventoryUtility.findItemInInventory(Items.TOTEM_OF_UNDYING).found() || offHandItem == Items.TOTEM_OF_UNDYING))
            item = Items.TOTEM_OF_UNDYING;

        if (!rcGap.is(RCGap.Off) && (mc.player.getMainHandStack().getItem() instanceof SwordItem) && mc.options.useKey.isPressed() && !(offHandItem instanceof ShieldItem)) {
            if (rcGap.is(RCGap.Always) || (rcGap.is(RCGap.OnlySafe) && getTriggerHealth() > healthF.getValue())) {
                if (crapple.found() || offHandItem == Items.GOLDEN_APPLE)
                    item = Items.GOLDEN_APPLE;
                if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE)
                    item = Items.ENCHANTED_GOLDEN_APPLE;
            }
        }

        if (onFall.getValue() && (getTriggerHealth()) - (((mc.player.fallDistance - 3) / 2F) + 3.5F) < 0.5)
            item = Items.TOTEM_OF_UNDYING;

        if (onElytra.getValue() && mc.player.isFallFlying())
            item = Items.TOTEM_OF_UNDYING;

        if (onCrystalInHand.getValue()) {
            for (PlayerEntity pl : Managers.ASYNC.getAsyncPlayers()) {
                if (Managers.FRIEND.isFriend(pl)) continue;
                if (pl == mc.player) continue;
                if (getPlayerPos().squaredDistanceTo(pl.getPos()) < 36) {
                    if (pl.getMainHandStack().getItem() == Items.OBSIDIAN
                            || pl.getMainHandStack().getItem() == Items.END_CRYSTAL
                            || pl.getOffHandStack().getItem() == Items.OBSIDIAN
                            || pl.getOffHandStack().getItem() == Items.END_CRYSTAL)
                        item = Items.TOTEM_OF_UNDYING;
                }
            }
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity == null || !entity.isAlive()) continue;
            if (getPlayerPos().squaredDistanceTo(entity.getPos()) > 36) continue;

            if (onCrystal.getValue()) {
                if (entity instanceof EndCrystalEntity) {
                    if (getTriggerHealth() - ExplosionUtility.getExplosionDamageWPredict(entity.getPos(), mc.player, PredictUtility.createBox(getPlayerPos(), mc.player), false) < 0.5) {
                        item = Items.TOTEM_OF_UNDYING;
                        break;
                    }
                }
            }

            if (onTnt.getValue()) {
                if (entity instanceof TntEntity) {
                    item = Items.TOTEM_OF_UNDYING;
                    break;
                }
            }

            if (onMinecartTnt.getValue()) {
                if (entity instanceof TntMinecartEntity) {
                    item = Items.TOTEM_OF_UNDYING;
                    break;
                }
            }

            if (onCreeper.getValue()) {
                if (entity instanceof CreeperEntity) {
                    item = Items.TOTEM_OF_UNDYING;
                    break;
                }
            }
        }

        if (onAnchor.getValue()) {
            for (int x = -6; x <= 6; x++)
                for (int y = -6; y <= 6; y++)
                    for (int z = -6; z <= 6; z++) {
                        BlockPos bp = new BlockPos(x, y, z);
                        if (mc.world.getBlockState(bp).getBlock() == Blocks.RESPAWN_ANCHOR) {
                            item = Items.TOTEM_OF_UNDYING;
                            break;
                        }
                    }
        }

        if (offhandOnly.getValue() && mc.player.getOffHandStack().getItem() == item) {
            return -1;
        }

        for (int i = 9; i < 45; i++) {
            if (mc.player.getOffHandStack().getItem() == item) return -1;
            if (mc.player.getInventory().getStack(i >= 36 ? i - 36 : i).getItem().equals(item)) {
                itemSlot = i >= 36 ? i - 36 : i;
                break;
            }
        }

        if (fallbackToHotbar.getValue() && itemSlot == -1) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).getItem().equals(item)) {
                    itemSlot = i;
                    break;
                }
            }
        }

        if (item == mc.player.getMainHandStack().getItem() && mc.options.useKey.isPressed()) return -1;

        return itemSlot;
    }

    private float getTriggerHealth() {
        return mc.player.getHealth() + (calcAbsorption.getValue() ? mc.player.getAbsorptionAmount() : 0f);
    }

    private void runInstant() {
        SearchInvResult hotbarResult = InventoryUtility.findItemInHotBar(Items.TOTEM_OF_UNDYING);
        SearchInvResult invResult = InventoryUtility.findItemInInventory(Items.TOTEM_OF_UNDYING);
        if (hotbarResult.found()) {
            hotbarResult.switchTo();
            delay = 20;
        } else if (invResult.found()) {
            int slot = invResult.slot() >= 36 ? invResult.slot() - 36 : invResult.slot();
            if (!hotbarFallBack.getValue()) swapTo(slot);
            else mc.interactionManager.pickFromInventory(slot);
            delay = 20;
        }
    }

    private Vec3d getPlayerPos() {
        return ModuleManager.blink.isEnabled() ? Blink.lastPos : mc.player.getPos();
    }
    
    private void sendMessage(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal(msg), false);
        }
    }
}
