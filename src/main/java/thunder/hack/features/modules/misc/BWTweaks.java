package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

public class BWTweaks extends Module {
    public BWTweaks() {
        super("BWTweaks", Category.MISC);
    }

    private final Setting<Integer> delay = new Setting<>("Delay", 800, 100, 2000);
    private final Setting<PVPVersion> pvpVersion = new Setting<>("PVPVersion", PVPVersion.V1_8);
    private final Setting<Boolean> debug = new Setting<>("Debug", true);

    private enum PVPVersion {
        V1_8, V1_12
    }

    private final Timer timer = new Timer();
    private int step = 0;
    private boolean teamJoined = false;
    private int tickCounter = 0;

    @Override
    public void onEnable() {
        step = 0;
        teamJoined = false;
        tickCounter = 0;
        timer.reset();
        if (debug.getValue()) displayMessage("§a[BWTweaks] Enabled");
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (fullNullCheck()) return;
        if (!(e.getPacket() instanceof net.minecraft.network.packet.s2c.play.GameMessageS2CPacket packet)) return;

        String message = packet.content().getString();
        if (message.contains("Вы присоединились к команде")) {
            teamJoined = true;
            tickCounter = 0;
            if (debug.getValue()) displayMessage("§a[BWTweaks] Team joined");
        }
    }

    @Override
    public void onUpdate() {
        if (fullNullCheck()) return;

        if (!teamJoined) return;

        tickCounter++;
        if (tickCounter < 20) return; // ждём 20 тиков

        if (step == 0) {
            if (debug.getValue()) displayMessage("§7[BWTweaks] Step 1: open shop");
            selectSlot(4); // слот 5
            step = 1;
            timer.reset();
        }
        
        else if (step == 1 && timer.passedMs(delay.getValue())) {
            if (debug.getValue()) displayMessage("§7[BWTweaks] Step 2: click PvP version");
            int slot = (pvpVersion.getValue() == PVPVersion.V1_8) ? 13 : 15;
            clickGuiSlot(slot);
            step = 2;
            timer.reset();
        }
        
        else if (step == 2 && timer.passedMs(delay.getValue())) {
            if (debug.getValue()) displayMessage("§7[BWTweaks] Step 3: close GUI");
            closeInventory();
            step = 3;
            timer.reset();
        }
        
        else if (step == 3 && timer.passedMs(delay.getValue())) {
            if (debug.getValue()) displayMessage("§7[BWTweaks] Step 4: select slot 6");
            selectSlot(5); // слот 6
            step = 4;
            timer.reset();
        }
        
        else if (step == 4 && timer.passedMs(delay.getValue())) {
            if (debug.getValue()) displayMessage("§7[BWTweaks] Step 5: click PvP mode");
            clickGuiSlot(13);
            step = 5;
            timer.reset();
        }
        
        else if (step == 5 && timer.passedMs(delay.getValue())) {
            if (debug.getValue()) displayMessage("§7[BWTweaks] Step 6: close GUI");
            closeInventory();
            step = 6;
            timer.reset();
        }
        
        else if (step == 6 && timer.passedMs(delay.getValue())) {
            if (debug.getValue()) displayMessage("§7[BWTweaks] Step 7: select slot 7");
            selectSlot(6); // слот 7
            step = 7;
            timer.reset();
        }
        
        else if (step == 7 && timer.passedMs(delay.getValue())) {
            if (debug.getValue()) displayMessage("§7[BWTweaks] Step 8: click resources");
            clickGuiSlot(15);
            step = 8;
            timer.reset();
        }
        
        else if (step == 8 && timer.passedMs(delay.getValue())) {
            if (debug.getValue()) displayMessage("§7[BWTweaks] Step 9: close GUI");
            closeInventory();
            step = 9;
            timer.reset();
        }
        
        else if (step == 9) {
            if (debug.getValue()) displayMessage("§a[BWTweaks] Vote completed!");
            disable();
        }
    }

    private void selectSlot(int slot) {
        sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        mc.player.getInventory().selectedSlot = slot;
        
        // правый клик в воздухе
        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
    }

    private void clickGuiSlot(int slot) {
        if (mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen screen) {
            mc.interactionManager.clickSlot(
                screen.getScreenHandler().syncId,
                slot,
                0,
                SlotActionType.PICKUP,
                mc.player
            );
            if (debug.getValue()) displayMessage("§7[BWTweaks] Clicked slot " + slot);
        } else {
            if (debug.getValue()) displayMessage("§c[BWTweaks] No GUI found");
        }
    }

    private void closeInventory() {
        if (mc.currentScreen != null) {
            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            mc.player.closeHandledScreen();
        }
    }

    private void displayMessage(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(msg), false);
        }
    }
}
