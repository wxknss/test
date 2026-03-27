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

    private final Setting<Integer> delay = new Setting<>("Delay", 100, 30, 300);

    // Слоты для кликов по порядку (с нуля)
    private final int[] slots = {4, 22, 21, 4, 29, 28, 21, 6, 22};
    
    private final Timer timer = new Timer();
    private int currentSlotIndex = 0;
    private boolean running = false;

    @Override
    public void onEnable() {
        currentSlotIndex = 0;
        running = true;
        displayMessage("§aAutoBuy started");
    }

    @Override
    public void onDisable() {
        running = false;
        currentSlotIndex = 0;
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
        
        if (!timer.passedMs(delay.getValue())) return;
        
        int slot = slots[currentSlotIndex];
        clickSlot(screen, slot);
        displayMessage("§eClicked slot: §7" + slot);
        
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
