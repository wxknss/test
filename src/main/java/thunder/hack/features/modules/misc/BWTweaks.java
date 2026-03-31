package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import thunder.hack.events.impl.PacketEvent;
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
    private boolean teamJoined = false;
    private boolean voteStarted = false;
    private boolean firstMenuClosed = false;

    @Override
    public void onEnable() {
        reset();
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (fullNullCheck()) return;
        
        if (e.getPacket() instanceof CloseHandledScreenC2SPacket && !firstMenuClosed && teamJoined && !voteStarted) {
            firstMenuClosed = true;
        }
        
        if (!(e.getPacket() instanceof net.minecraft.network.packet.s2c.play.GameMessageS2CPacket packet)) return;

        String message = packet.content().getString();

        if (message.contains("Вы присоединились к команде")) {
            teamJoined = true;
            firstMenuClosed = false;
            return;
        }

        if (message.contains("Выберите версию PvP") || message.contains("голосование")) {
            if (teamJoined && firstMenuClosed && !voteStarted) {
                voteStarted = true;
                step = 1;
                timer.reset();
            }
        }
    }

    @Override
    public void onUpdate() {
        if (fullNullCheck()) return;
        
        if (!voteStarted || step == 0) return;

        if (!timer.passedMs(delay.getValue())) return;

        switch (step) {
            case 1:
                openShop();
                step = 2;
                timer.reset();
                break;
                
            case 2:
                clickInGui((pvpVersion.getValue() == PVPVersion.V1_8) ? 13 : 15);
                step = 3;
                timer.reset();
                break;
                
            case 3:
                closeInventory();
                step = 4;
                timer.reset();
                break;
                
            case 4:
                selectSlot(5);
                step = 5;
                timer.reset();
                break;
                
            case 5:
                clickInGui(13);
                step = 6;
                timer.reset();
                break;
                
            case 6:
                closeInventory();
                step = 7;
                timer.reset();
                break;
                
            case 7:
                selectSlot(6);
                step = 8;
                timer.reset();
                break;
                
            case 8:
                clickInGui(15);
                step = 9;
                timer.reset();
                break;
                
            case 9:
                closeInventory();
                step = 10;
                timer.reset();
                break;
                
            case 10:
                voteStarted = false;
                teamJoined = false;
                firstMenuClosed = false;
                step = 0;
                break;
        }
    }

    private void openShop() {
        selectSlot(4);
    }

    private void selectSlot(int slot) {
        sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        mc.player.getInventory().selectedSlot = slot;
        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
    }

    private void clickInGui(int slot) {
        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            mc.interactionManager.clickSlot(
                screen.getScreenHandler().syncId,
                slot,
                0,
                SlotActionType.PICKUP,
                mc.player
            );
        }
    }

    private void closeInventory() {
        if (mc.currentScreen != null) {
            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            mc.player.closeHandledScreen();
        }
    }

    private void reset() {
        step = 0;
        teamJoined = false;
        voteStarted = false;
        firstMenuClosed = false;
        timer.reset();
    }
}
