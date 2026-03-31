package thunder.hack.features.modules.misc;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

public class BWTweaks extends Module {
    public BWTweaks() {
        super("BWTweaks", Category.MISC);
    }

    private final Setting<Integer> delay = new Setting<>("Delay", 500, 100, 2000);
    private final Setting<PVPVersion> pvpVersion = new Setting<>("PVPVersion", PVPVersion.V1_8);

    private enum PVPVersion {
        V1_8, V1_12
    }

    private final Timer timer = new Timer();
    private int step = 0;
    private boolean waitingForMenu = false;

    @Override
    public void onUpdate() {
        if (fullNullCheck()) return;

        if (!waitingForMenu) {
            if (step == 0) {
                step = 1;
                waitingForMenu = true;
                timer.reset();
            }
            return;
        }

        if (!timer.passedMs(delay.getValue())) return;

        switch (step) {
            case 1:
                // открываем шоп
                selectSlot(4);
                step = 2;
                timer.reset();
                break;

            case 2:
                // кликаем в GUI
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    int slot = (pvpVersion.getValue() == PVPVersion.V1_8) ? 13 : 15;
                    clickSlot(screen, slot);
                    step = 3;
                    timer.reset();
                }
                break;

            case 3:
                // закрываем
                if (mc.currentScreen != null) {
                    mc.player.closeHandledScreen();
                }
                step = 4;
                timer.reset();
                break;

            case 4:
                selectSlot(5);
                step = 5;
                timer.reset();
                break;

            case 5:
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    clickSlot(screen, 13);
                    step = 6;
                    timer.reset();
                }
                break;

            case 6:
                if (mc.currentScreen != null) {
                    mc.player.closeHandledScreen();
                }
                step = 7;
                timer.reset();
                break;

            case 7:
                selectSlot(6);
                step = 8;
                timer.reset();
                break;

            case 8:
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    clickSlot(screen, 15);
                    step = 9;
                    timer.reset();
                }
                break;

            case 9:
                if (mc.currentScreen != null) {
                    mc.player.closeHandledScreen();
                }
                step = 10;
                timer.reset();
                break;

            case 10:
                waitingForMenu = false;
                step = 0;
                disable();
                break;
        }
    }

    private void selectSlot(int slot) {
        mc.player.getInventory().selectedSlot = slot;
        // имитируем правый клик
        if (mc.crosshairTarget != null) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, (net.minecraft.util.hit.BlockHitResult) mc.crosshairTarget);
        }
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

    private void sendMessage(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(msg), false);
        }
    }
}
