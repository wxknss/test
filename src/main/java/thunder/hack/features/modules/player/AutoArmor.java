package thunder.hack.features.modules.player;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.gui.clickui.ClickGUI;
import thunder.hack.gui.hud.HudEditorGui;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.MovementUtility;

import java.util.Arrays;
import java.util.List;

public class AutoArmor extends Module {
    public AutoArmor() {
        super("AutoArmor", Category.PLAYER);
    }

    private final Setting<EnchantPriority> head = new Setting<>("Head", EnchantPriority.Protection);
    private final Setting<EnchantPriority> body = new Setting<>("Body", EnchantPriority.Protection);
    private final Setting<EnchantPriority> tights = new Setting<>("Tights", EnchantPriority.Protection);
    private final Setting<EnchantPriority> feet = new Setting<>("Feet", EnchantPriority.Protection);
    private final Setting<ElytraPriority> elytraPriority = new Setting<>("ElytraPriority", ElytraPriority.Ignore);
    private final Setting<Integer> delay = new Setting<>("Delay", 5, 0, 10);
    private final Setting<Boolean> oldVersion = new Setting<>("OldVersion", false);
    private final Setting<Boolean> pauseInventory = new Setting<>("PauseInventory", false);
    private final Setting<Boolean> noMove = new Setting<>("NoMove", false);
    private final Setting<Boolean> ignoreCurse = new Setting<>("IgnoreCurse", true);
    private final Setting<Boolean> strict = new Setting<>("Strict", false);
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Basic);
    private final Setting<Boolean> fakeItemsBypass = new Setting<>("FakeItemsBypass", false);

    private int tickDelay = 0;
    private long[] lastSlotChangeTime = new long[45];

    List<ArmorData> armorList = Arrays.asList(
            new ArmorData(EquipmentSlot.FEET, 36, -1, -1, -1),
            new ArmorData(EquipmentSlot.LEGS, 37, -1, -1, -1),
            new ArmorData(EquipmentSlot.CHEST, 38, -1, -1, -1),
            new ArmorData(EquipmentSlot.HEAD, 39, -1, -1, -1)
    );

    @Override
    public void onUpdate() {
        if (mode.getValue() == Mode.OpenInv && !(mc.currentScreen instanceof ChatScreen) && !(mc.currentScreen instanceof ClickGUI) && !(mc.currentScreen instanceof HudEditorGui) && mc.currentScreen != null)
            return;

        if (mc.currentScreen != null && pauseInventory.getValue() && !(mc.currentScreen instanceof ChatScreen) && !(mc.currentScreen instanceof ClickGUI) && !(mc.currentScreen instanceof HudEditorGui))
            return;

        if (tickDelay-- > 0)
            return;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) continue;

            ItemStack currentStack = mc.player.getEquippedStack(slot);
            int currentValue = getArmorValue(currentStack, slot);

            int bestSlot = -1;
            int bestValue = currentValue;

            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (isValidForSlot(stack, slot)) {
                    if (fakeItemsBypass.getValue() && System.currentTimeMillis() - lastSlotChangeTime[i] < 1500)
                        continue;

                    int value = getArmorValue(stack, slot);
                    if (value > bestValue) {
                        bestValue = value;
                        bestSlot = i;
                    }
                }
            }

            if (bestSlot != -1) {
                if (MovementUtility.isMoving() && noMove.getValue())
                    return;

                equipArmor(bestSlot, slot);
                tickDelay = delay.getValue();
                lastSlotChangeTime[bestSlot] = System.currentTimeMillis();
                return;
            }
        }
    }

    private boolean isValidForSlot(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return false;

        if (stack.getItem() instanceof ElytraItem) {
            if (slot != EquipmentSlot.CHEST) return false;
            if (elytraPriority.getValue() == ElytraPriority.Ignore) return false;
            if (elytraPriority.getValue() == ElytraPriority.OnUse && !mc.player.isFallFlying()) return false;
            return true;
        }

        if (!(stack.getItem() instanceof ArmorItem armor)) return false;
        return armor.getSlotType() == slot;
    }

    private int getArmorValue(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return -1;

        if (stack.getItem() instanceof ElytraItem) {
            return 1000;
        }

        if (!(stack.getItem() instanceof ArmorItem armor)) return -1;
        if (armor.getSlotType() != slot) return -1;

        int value = armor.getProtection() * 10 + (int) Math.ceil(armor.getToughness());

        if (stack.hasEnchantments()) {
            ItemEnchantmentsComponent enchants = EnchantmentHelper.getEnchantments(stack);

            RegistryEntry<Enchantment> protection = mc.world.getRegistryManager().get(Enchantments.PROTECTION.getRegistryRef()).getEntry(Enchantments.PROTECTION).get();
            RegistryEntry<Enchantment> blastProtection = mc.world.getRegistryManager().get(Enchantments.BLAST_PROTECTION.getRegistryRef()).getEntry(Enchantments.BLAST_PROTECTION).get();
            RegistryEntry<Enchantment> fireProtection = mc.world.getRegistryManager().get(Enchantments.FIRE_PROTECTION.getRegistryRef()).getEntry(Enchantments.FIRE_PROTECTION).get();
            RegistryEntry<Enchantment> projectileProtection = mc.world.getRegistryManager().get(Enchantments.PROJECTILE_PROTECTION.getRegistryRef()).getEntry(Enchantments.PROJECTILE_PROTECTION).get();
            RegistryEntry<Enchantment> bindingCurse = mc.world.getRegistryManager().get(Enchantments.BINDING_CURSE.getRegistryRef()).getEntry(Enchantments.BINDING_CURSE).get();

            int protectionMultiplier = 1;
            int blastMultiplier = 1;

            switch (slot) {
                case HEAD:
                    if (head.getValue() == EnchantPriority.Protection) protectionMultiplier = 2;
                    else blastMultiplier = 2;
                    break;
                case CHEST:
                    if (body.getValue() == EnchantPriority.Protection) protectionMultiplier = 2;
                    else blastMultiplier = 2;
                    break;
                case LEGS:
                    if (tights.getValue() == EnchantPriority.Protection) protectionMultiplier = 2;
                    else blastMultiplier = 2;
                    break;
                case FEET:
                    if (feet.getValue() == EnchantPriority.Protection) protectionMultiplier = 2;
                    else blastMultiplier = 2;
                    break;
            }

            if (enchants.getEnchantments().contains(protection))
                value += enchants.getLevel(protection) * 20 * protectionMultiplier;

            if (enchants.getEnchantments().contains(blastProtection))
                value += enchants.getLevel(blastProtection) * 15 * blastMultiplier;

            if (enchants.getEnchantments().contains(fireProtection))
                value += enchants.getLevel(fireProtection) * 15;

            if (enchants.getEnchantments().contains(projectileProtection))
                value += enchants.getLevel(projectileProtection) * 15;

            if (enchants.getEnchantments().contains(bindingCurse) && !ignoreCurse.getValue())
                return -999;
        }

        int durability = stack.getMaxDamage() - stack.getDamage();
        value += (durability * 10 / stack.getMaxDamage());

        return value;
    }

    private void equipArmor(int slot, EquipmentSlot armorSlot) {
        int armorInventorySlot = 36 + armorSlot.getEntitySlotId();

        if (slot < 9) {
            InventoryUtility.saveAndSwitchTo(slot);
            sendSequencedPacket(id -> new net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket(net.minecraft.util.Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
            InventoryUtility.returnSlot();
        } else {
            if (strict.getValue())
                sendPacket(new net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket(mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.STOP_SPRINTING));

            clickSlot(slot);
            clickSlot(armorInventorySlot);
            if (mc.player.getInventory().getStack(armorInventorySlot).isEmpty())
                clickSlot(slot);

            sendPacket(new net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
        }
    }

    public class ArmorData {
        private EquipmentSlot equipmentSlot;
        private int armorSlot, prevProtection, newSlot, newProtection;

        public ArmorData(EquipmentSlot equipmentSlot, int armorSlot, int prevProtection, int newSlot, int newProtection) {
            this.equipmentSlot = equipmentSlot;
            this.armorSlot = armorSlot;
            this.prevProtection = prevProtection;
            this.newSlot = newSlot;
            this.newProtection = newProtection;
        }

        public int getArmorSlot() {
            return armorSlot;
        }

        public int getPrevProt() {
            return prevProtection;
        }

        public void setPrevProt(int prevProtection) {
            this.prevProtection = prevProtection;
        }

        public int getNewSlot() {
            return newSlot;
        }

        public void setNewSlot(int newSlot) {
            this.newSlot = newSlot;
        }

        public int getNewProtection() {
            return newProtection;
        }

        public void setNewProtection(int newProtection) {
            this.newProtection = newProtection;
        }

        public EquipmentSlot getEquipmentSlot() {
            return equipmentSlot;
        }

        public void reset() {
            setPrevProt(getArmorValue(mc.player.getInventory().getStack(getArmorSlot()), getEquipmentSlot()));
            setNewSlot(-1);
            setNewProtection(-1);
        }
    }

    private enum ElytraPriority {
        Ignore, Always, OnUse
    }

    private enum EnchantPriority {
        Blast, Protection
    }

    private enum Mode {
        Basic, OpenInv
    }
}
