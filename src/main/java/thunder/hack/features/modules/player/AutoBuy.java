package thunder.hack.features.modules.player;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

public class AutoBuy extends Module {
    public AutoBuy() {
        super("AutoBuy", Category.PLAYER);
    }

    private final Setting<Integer> delay = new Setting<>("Delay", 350, 50, 1000);
    private final Setting<Boolean> requireExp = new Setting<>("RequireExp", true);
    private final Setting<Integer> minExp = new Setting<>("MinExp", 3000, 0, 10000, v -> requireExp.getValue());

    private final int[] slots = {3, 22, 21, 4, 29, 28, 21, 6, 22};
    private final Timer timer = new Timer();
    private int currentSlotIndex = 0;
    private boolean running = false;
    private boolean waitingForShop = true;

    @Override
    public void onEnable() {
        if (requireExp.getValue() && mc.player != null && mc.player.experienceLevel < minExp.getValue()) {
            displayMessage("§cМало ресов! Надо нализать §7" + minExp.getValue() + " §cштук");
            disable();
            return;
        }
        
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
    }

    @Override
    public void onUpdate() {
        if (!running || fullNullCheck()) return;
        
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            if (currentSlotIndex > 0) {
                disable();
            }
            return;
        }
        
        if (currentSlotIndex >= slots.length) {
            disable();
            return;
        }
        
        if (waitingForShop) {
            if (timer.passedMs(delay.getValue())) {
                waitingForShop = false;
                timer.reset();
            } else {
                return;
            }
        }
        
        if (!timer.passedMs(delay.getValue())) return;
        
        clickSlot(screen, slots[currentSlotIndex]);
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
