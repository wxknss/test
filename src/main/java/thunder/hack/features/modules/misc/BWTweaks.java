package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
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

    private final Setting<Integer> delay = new Setting<>("Delay", 500, 100, 2000);
    private final Setting<PVPVersion> pvpVersion = new Setting<>("PVPVersion", PVPVersion.V1_8);
    private final Setting<Boolean> debug = new Setting<>("Debug", true);

    private enum PVPVersion {
        V1_8, V1_12
    }

    private final Timer timer = new Timer();
    private State state = State.IDLE;
    private int tickCounter = 0;
    private boolean teamJoined = false;
    private boolean voteStarted = false;

    private enum State {
        IDLE, WAITING, SELECTING_SLOT_5, CLICK_GUI_13, CLOSE_GUI_1, SELECTING_SLOT_6, CLICK_PVP, CLOSE_GUI_2, SELECTING_SLOT_7, CLICK_RES, CLOSE_GUI_3, DONE
    }

    @Override
    public void onEnable() {
        reset();
        if (debug.getValue()) displayMessage("§a[BWTweaks] Enabled");
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (fullNullCheck()) return;
        if (!(e.getPacket() instanceof net.minecraft.network.packet.s2c.play.GameMessageS2CPacket packet)) return;

        String message = packet.content().getString();

        if (debug.getValue()) {
            displayMessage("§7[BWTweaks] Chat: §f" + message);
        }

        // Присоединились к команде (ищем часть строки, т.к. дальше идёт цвет команды)
        if (message.contains("Вы присоединились к команде")) {
            teamJoined = true;
            tickCounter = 0;
            if (debug.getValue()) displayMessage("§a[BWTweaks] Team joined detected");
            return;
        }

        // Линия разделитель — голосование отменяется (ищем часть строки)
        if (message.contains("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")) {
            voteStarted = false;
            if (debug.getValue()) displayMessage("§c[BWTweaks] Vote cancelled by separator");
            return;
        }

        // Голосование (ищем ключевые слова)
        if (message.contains("Выберите версию PvP") || message.contains("выберите версию") || message.contains("голосование")) {
            if (teamJoined && !voteStarted) {
                voteStarted = true;
                if (debug.getValue()) displayMessage("§a[BWTweaks] Vote detected, starting in 20 ticks");
            }
        }
    }

    @Override
    public void onUpdate() {
        if (fullNullCheck()) return;

        // Ждём 20 тиков после входа в команду
        if (teamJoined && !voteStarted) {
            tickCounter++;
            if (tickCounter >= 20) {
                if (debug.getValue()) displayMessage("§a[BWTweaks] Starting vote sequence");
                startVote();
            }
            return;
        }

        if (state == State.IDLE) return;

        if (!timer.passedMs(delay.getValue())) return;

        switch (state) {
            case SELECTING_SLOT_5:
                selectSlot(4);
                state = State.CLICK_GUI_13;
                timer.reset();
                break;

            case CLICK_GUI_13:
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    int slot = (pvpVersion.getValue() == PVPVersion.V1_8) ? 13 : 15;
                    clickSlot(screen, slot);
                    state = State.CLOSE_GUI_1;
                } else {
                    if (debug.getValue()) displayMessage("§c[BWTweaks] No GUI found, trying again");
                    state = State.SELECTING_SLOT_5;
                }
                timer.reset();
                break;

            case CLOSE_GUI_1:
                closeInventory();
                state = State.SELECTING_SLOT_6;
                timer.reset();
                break;

            case SELECTING_SLOT_6:
                selectSlot(5);
                state = State.CLICK_PVP;
                timer.reset();
                break;

            case CLICK_PVP:
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    clickSlot(screen, 13);
                    state = State.CLOSE_GUI_2;
                } else {
                    state = State.SELECTING_SLOT_6;
                }
                timer.reset();
                break;

            case CLOSE_GUI_2:
                closeInventory();
                state = State.SELECTING_SLOT_7;
                timer.reset();
                break;

            case SELECTING_SLOT_7:
                selectSlot(6);
                state = State.CLICK_RES;
                timer.reset();
                break;

            case CLICK_RES:
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    clickSlot(screen, 15);
                    state = State.CLOSE_GUI_3;
                } else {
                    state = State.SELECTING_SLOT_7;
                }
                timer.reset();
                break;

            case CLOSE_GUI_3:
                closeInventory();
                state = State.DONE;
                timer.reset();
                break;

            case DONE:
                reset();
                disable();
                displayMessage("§a[BWTweaks] Vote completed!");
                break;
        }
    }

    private void startVote() {
        state = State.SELECTING_SLOT_5;
        timer.reset();
    }

    private void selectSlot(int slot) {
        sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        mc.player.getInventory().selectedSlot = slot;
        
        if (mc.crosshairTarget instanceof BlockHitResult hit) {
            sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, id));
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
        if (debug.getValue()) displayMessage("§7[BWTweaks] Clicked slot " + slot);
    }

    private void closeInventory() {
        if (mc.currentScreen != null) {
            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            mc.player.closeHandledScreen();
        }
    }

    private void reset() {
        state = State.IDLE;
        teamJoined = false;
        voteStarted = false;
        tickCounter = 0;
        timer.reset();
    }

    private void displayMessage(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(msg), false);
        }
    }
}
