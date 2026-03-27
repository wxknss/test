package thunder.hack.features.modules.misc;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

public class AutoBuy extends Module {
    public AutoBuy() {
        super("AutoBuy", Category.MISC);
    }

    private final Setting<Integer> delay = new Setting<>("Delay", 250, 50, 1000);

    // Слоты для кликов по порядку (с 0)
    // Твои номера (с 1): 4, 23, 22, 5, 30, 29, 22, 7, 23
    private final int[] slots = {3, 22, 21, 4, 29, 28, 21, 6, 22};
    
    private final Timer timer = new Timer();
    private int currentSlotIndex = 0;
    private boolean running = false;
    private boolean waitingForShop = true;

    @Override
    public void onEnable() {
        currentSlotIndex = 0;
        running = true;
        waitingForShop = true;
        timer.reset();
        displayMessage("§aAutoBuy started");
    }

    @Override
    public void onDisable() {
        running = false;
        currentSlotIndex = 0;
        waitingForShop = true;
        closeInventory();
        displayMessage("§cAutoBuy stopped");
    }

    @Override
    public void onUpdate() {
        if (!running || fullNullCheck()) return;
        
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            if (currentSlotIndex > 0) {
                displayMessage("§cShop closed! AutoBuy disabled.");
                disable();
            }
            return;
        }
        
        if (currentSlotIndex >= slots.length) {
            displayMessage("§aAll slots clicked! AutoBuy disabled.");
            disable();
            return;
        }
        
        // Ждем задержку после открытия шопа перед первым кликом
        if (waitingForShop) {
            if (timer.passedMs(delay.getValue())) {
                waitingForShop = false;
                timer.reset();
            } else {
                return;
            }
        }
        
        if (!timer.passedMs(delay.getValue())) return;
        
        int slot = slots[currentSlotIndex];
        clickSlot(screen, slot);
        displayMessage("§e[" + (currentSlotIndex + 1) + "/" + slots.length + "] Clicked slot: §7" + (slot + 1));
        
        currentSlotIndex++;
        timer.reset();
    }
    
    private void clickSlot(GenericContainerScreen screen, int slot) {
        mc.interactionManager.clickSlot(
            screen.getScreenHandler().syncId,
            slot,
            0,
            SlotActionType.PICKUP,
            mc.player
        );
    }
    
    private void closeInventory() {
        if (mc.player != null && mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
    }
    
    private void displayMessage(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(msg), false);
        }
    }
}
