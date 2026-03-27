package thunder.hack.features.modules.misc;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

public class AutoBuy extends Module {
    public AutoBuy() {
        super("AutoBuy", Category.MISC);
    }

    // ===== НАСТРОЙКИ =====
    private final Setting<Boolean> chainBoots = new Setting<>("Chain Boots", true);      // КНОПКА РАЗДЕЛА
    private final Setting<Boolean> elytra = new Setting<>("Elytra", false);
    private final Setting<Boolean> diamond = new Setting<>("Diamond", false);
    private final Setting<Boolean> stonePick = new Setting<>("Stone Pickaxe", true);     // КНОПКА РАЗДЕЛА
    private final Setting<Boolean> ironAxe = new Setting<>("Iron Axe", false);
    private final Setting<Boolean> ironPick = new Setting<>("Iron Pickaxe", false);
    private final Setting<Boolean> shears = new Setting<>("Shears", false);
    private final Setting<Boolean> strengthPot = new Setting<>("Strength Potion", false); // КНОПКА РАЗДЕЛА
    private final Setting<Integer> delay = new Setting<>("Delay", 100, 30, 300);

    private final Timer timer = new Timer();
    private BuyState state = BuyState.IDLE;
    private String currentTarget = "";
    private int itemsBought = 0;
    private int targetCount = 0;

    private enum BuyState {
        IDLE, WAITING_FOR_SHOP, BUYING, VERIFYING, DONE
    }

    @Override
    public void onEnable() {
        reset();
        state = BuyState.WAITING_FOR_SHOP;
        targetCount = calculateTargetCount();
        sendMessage("§aAutoBuy enabled. Need to buy: §7" + targetCount + " §aitems");
    }

    @Override
    public void onDisable() {
        reset();
        closeInventory();
    }

    @Override
    public void onUpdate() {
        if (fullNullCheck()) return;
        
        if (state == BuyState.DONE || state == BuyState.IDLE) {
            if (state == BuyState.DONE) {
                sendMessage("§aAutoBuy completed! Disabling...");
                disable();
            }
            return;
        }

        if (state == BuyState.WAITING_FOR_SHOP) {
            if (mc.currentScreen instanceof GenericContainerScreen screen) {
                String title = screen.getTitle().getString().toLowerCase();
                if (title.contains("shop") || title.contains("items") || title.contains("store")) {
                    state = BuyState.BUYING;
                    timer.reset();
                }
            }
            return;
        }

        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            if (state != BuyState.WAITING_FOR_SHOP) {
                sendMessage("§cShop closed! AutoBuy disabled.");
                disable();
            }
            return;
        }

        if (!timer.passedMs(delay.getValue())) return;

        switch (state) {
            case BUYING:
                currentTarget = getNextTarget();
                if (currentTarget == null) {
                    state = BuyState.DONE;
                    return;
                }
                
                sendMessage("§eBuying: §7" + currentTarget);
                
                int slot = findItemSlot(screen, currentTarget);
                if (slot != -1) {
                    clickSlot(screen, slot);
                    
                    // Разделы (кнопки) не проверяем в инвентаре
                    if (isSectionButton(currentTarget)) {
                        sendMessage("§aOpened section: §7" + currentTarget);
                        state = BuyState.BUYING;
                    } else {
                        state = BuyState.VERIFYING;
                    }
                } else {
                    sendMessage("§cItem not found in shop: §7" + currentTarget);
                    state = BuyState.BUYING;
                }
                timer.reset();
                break;

            case VERIFYING:
                if (isItemInInventory(currentTarget)) {
                    itemsBought++;
                    sendMessage("§aBought: §7" + currentTarget);
                } else {
                    sendMessage("§cFailed to buy: §7" + currentTarget);
                }
                state = BuyState.BUYING;
                timer.reset();
                break;
        }
    }

    private boolean isSectionButton(String item) {
        return item.equals("chainBoots") || item.equals("stonePick") || item.equals("strengthPot");
    }

    private String getNextTarget() {
        // Кнопки разделов (кликаем, но не проверяем в инвентаре)
        if (chainBoots.getValue() && !isSectionDone("chainBoots")) return "chainBoots";
        if (stonePick.getValue() && !isSectionDone("stonePick")) return "stonePick";
        if (strengthPot.getValue() && !isSectionDone("strengthPot")) return "strengthPot";
        
        // Обычные предметы (проверяем в инвентаре)
        if (elytra.getValue() && !isItemInInventory("elytra")) return "elytra";
        if (diamond.getValue() && !isItemInInventory("diamond")) return "diamond";
        if (ironAxe.getValue() && !isItemInInventory("ironAxe")) return "ironAxe";
        if (ironPick.getValue() && !isItemInInventory("ironPick")) return "ironPick";
        if (shears.getValue() && !isItemInInventory("shears")) return "shears";
        return null;
    }

    private boolean isSectionDone(String section) {
        // Разделы считаются "сделанными" если мы их уже кликнули
        return sectionsClicked.contains(section);
    }

    private java.util.HashSet<String> sectionsClicked = new java.util.HashSet<>();

    private boolean isItemInInventory(String item) {
        if (mc.player == null) return false;
        
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (matchesItem(stack, item)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesItem(ItemStack stack, String item) {
        if (stack.isEmpty()) return false;
        
        switch (item) {
            case "elytra":
                return stack.getItem() == Items.ELYTRA;
            case "diamond":
                return stack.getItem() == Items.DIAMOND;
            case "ironAxe":
                return stack.getItem() == Items.IRON_AXE;
            case "ironPick":
                return stack.getItem() == Items.IRON_PICKAXE;
            case "shears":
                return stack.getItem() == Items.SHEARS;
            default:
                return false;
        }
    }

    private int findItemSlot(GenericContainerScreen screen, String item) {
        for (int i = 0; i < screen.getScreenHandler().slots.size(); i++) {
            ItemStack stack = screen.getScreenHandler().getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            
            if (matchesShopItem(stack, item)) {
                return i;
            }
        }
        return -1;
    }

    private boolean matchesShopItem(ItemStack stack, String item) {
        String name = stack.getName().getString().toLowerCase();
        
        switch (item) {
            case "chainBoots":
                return name.contains("chain") && name.contains("boots") || name.contains("кольчуга");
            case "elytra":
                return name.contains("elytra") || name.contains("элитра");
            case "diamond":
                return name.contains("diamond") || name.contains("алмаз");
            case "stonePick":
                return (name.contains("stone") || name.contains("каменная")) && name.contains("pickaxe");
            case "ironAxe":
                return (name.contains("iron") || name.contains("железный")) && name.contains("axe");
            case "ironPick":
                return (name.contains("iron") || name.contains("железная")) && name.contains("pickaxe");
            case "shears":
                return name.contains("shear") || name.contains("ножницы");
            case "strengthPot":
                return name.contains("strength") || name.contains("силы");
            default:
                return false;
        }
    }

    private int calculateTargetCount() {
        int count = 0;
        if (elytra.getValue()) count++;
        if (diamond.getValue()) count++;
        if (ironAxe.getValue()) count++;
        if (ironPick.getValue()) count++;
        if (shears.getValue()) count++;
        return count;
    }

    private void clickSlot(GenericContainerScreen screen, int slot) {
        mc.interactionManager.clickSlot(
            screen.getScreenHandler().syncId,
            slot,
            0,
            SlotActionType.PICKUP,
            mc.player
        );
        
        // Если это кнопка раздела — запоминаем
        if (isSectionButton(currentTarget)) {
            sectionsClicked.add(currentTarget);
        }
    }

    private void closeInventory() {
        if (mc.player != null) {
            mc.player.closeHandledScreen();
        }
    }

    private void reset() {
        itemsBought = 0;
        currentTarget = "";
        sectionsClicked.clear();
    }

    private void sendMessage(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(msg), false);
        }
    }
}
