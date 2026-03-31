package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import thunder.hack.events.impl.EventTick;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

import java.util.ArrayList;
import java.util.List;

public class ItemDropper extends Module {
    public ItemDropper() {
        super("ItemDropper", Category.MISC);
    }

    private final Setting<Boolean> dropHeld = new Setting<>("DropHeld", false);
    private final Setting<String> itemIds = new Setting<>("ItemIds", "minecraft:diamond,minecraft:iron_ingot", v -> !dropHeld.getValue());
    private final Setting<Integer> speed = new Setting<>("Speed", 1, 1, 30);
    private final Setting<Integer> stackSize = new Setting<>("StackSize", 1, 1, 64);
    private final Setting<Boolean> customName = new Setting<>("CustomName", false);
    private final Setting<String> customNameText = new Setting<>("Name", "§fCUSTOM §cNAME", v -> customName.getValue());
    private final Setting<String> enchantIds = new Setting<>("EnchantIds", "", v -> !dropHeld.getValue());
    private final Setting<Integer> enchantLevel = new Setting<>("EnchantLevel", 1, 1, 10, v -> !dropHeld.getValue());

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        if (!mc.player.isCreative()) {
            disable("Creative mode only.");
        }
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (fullNullCheck()) return;
        if (!mc.player.isCreative()) {
            disable("Creative mode only.");
            return;
        }

        List<String> selectedIds = parseItemIds(itemIds.getValue());
        if (selectedIds.isEmpty() && !dropHeld.getValue()) return;

        for (int i = 0; i < speed.getValue(); i++) {
            ItemStack stack;
            
            if (dropHeld.getValue()) {
                ItemStack held = mc.player.getMainHandStack();
                if (held.isEmpty()) continue;
                stack = held.copy();
                stack.setCount(stackSize.getValue());
            } else {
                String id = selectedIds.get(i % selectedIds.size());
                Item item = getItemById(id);
                if (item == Items.AIR) continue;
                stack = new ItemStack(item, stackSize.getValue());
            }
            
            applyEnchantments(stack);
            applyCustomName(stack);
            
            mc.player.getInventory().offerOrDrop(stack);
        }
    }
    
    private List<String> parseItemIds(String input) {
        List<String> result = new ArrayList<>();
        if (input == null || input.isEmpty()) return result;
        String[] parts = input.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
    
    private Item getItemById(String id) {
        try {
            Identifier identifier = id.contains(":") ? Identifier.of(id) : Identifier.of("minecraft", id);
            return net.minecraft.registry.Registries.ITEM.get(identifier);
        } catch (Exception e) {
            return Items.AIR;
        }
    }
    
    private void applyEnchantments(ItemStack stack) {
        if (dropHeld.getValue()) return;
        String csv = enchantIds.getValue();
        if (csv == null || csv.isEmpty()) return;
        
        String[] parts = csv.split(",");
        for (String raw : parts) {
            String encId = raw.trim();
            if (encId.isEmpty()) continue;
            try {
                Identifier id = encId.contains(":") ? Identifier.of(encId) : Identifier.of("minecraft", encId);
                RegistryKey<Enchantment> key = RegistryKey.of(RegistryKeys.ENCHANTMENT, id);
                Enchantment enchantment = net.minecraft.registry.Registries.ENCHANTMENT.get(key);
                if (enchantment != null) {
                    stack.addEnchantment(enchantment, enchantLevel.getValue());
                }
            } catch (Exception ignored) {}
        }
    }
    
    private void applyCustomName(ItemStack stack) {
        if (customName.getValue()) {
            String name = customNameText.getValue();
            if (name != null && !name.isEmpty()) {
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
            }
        }
    }
}
