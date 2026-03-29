package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import thunder.hack.core.Managers;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

import java.util.Objects;

public class MessageAppend extends Module {
    public MessageAppend() {
        super("MessageAppend", Category.MISC);
    }

    public Setting<Boolean> append_check = new Setting("MessageAppend", false);
    public Setting<String> messageappends = new Setting("AppendText", "");
    public Setting<Boolean> prefix_check = new Setting("MessagePrefix", false);
    public Setting<String> messageprefix = new Setting("PrefixText", "");
    public Setting<Boolean> autoGlobal = new Setting("AutoGlobal", false);

    private String skip;
    private String modifiedMessage;

    @EventHandler
    public void onPacketSend(PacketEvent.Send e) {
        if (fullNullCheck()) return;
        if (!(e.getPacket() instanceof ChatMessageC2SPacket pac)) return;

        if (Objects.equals(pac.chatMessage(), skip)) return;

        if (mc.player.getMainHandStack().getItem() == Items.FILLED_MAP || mc.player.getOffHandStack().getItem() == Items.FILLED_MAP) return;

        if (pac.chatMessage().startsWith("/") || pac.chatMessage().startsWith(Managers.COMMAND.getPrefix())) return;

        modifiedMessage = pac.chatMessage();

        if (prefix_check.getValue() && messageprefix.getValue() != null && !messageprefix.getValue().isEmpty()) {
            if (modifiedMessage.startsWith("!")) {
                modifiedMessage = modifiedMessage.substring(1);
                modifiedMessage = "!" + messageprefix.getValue() + " " + modifiedMessage;
            } else {
                modifiedMessage = messageprefix.getValue() + " " + modifiedMessage;
            }
        }

        if (append_check.getValue() && messageappends.getValue() != null && !messageappends.getValue().isEmpty()) {
            modifiedMessage = modifiedMessage + " " + messageappends.getValue();
        }

        if (autoGlobal.getValue()) {
            if (!modifiedMessage.startsWith("!")) {
                modifiedMessage = "!" + modifiedMessage;
            }
        }

        skip = modifiedMessage;
        mc.player.networkHandler.sendChatMessage(modifiedMessage);
        e.cancel();
    }
}
