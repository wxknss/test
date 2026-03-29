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

    private final Setting<Boolean> appendCheck = new Setting<>("Append", false);
    private final Setting<String> appendText = new Setting<>("AppendText", "", v -> appendCheck.getValue());
    private final Setting<Boolean> prefixCheck = new Setting<>("Prefix", false);
    private final Setting<String> prefixText = new Setting<>("PrefixText", "", v -> prefixCheck.getValue());
    private final Setting<Boolean> autoGlobal = new Setting<>("AutoGlobal", false);
    private final Setting<TextType> textType = new Setting<>("TextType", TextType.Default);

    private String skip;
    private String modifiedMessage;

    public enum TextType {
        Default,
        Custom
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send e) {
        if (fullNullCheck()) return;
        if (!(e.getPacket() instanceof ChatMessageC2SPacket pac)) return;

        if (Objects.equals(pac.chatMessage(), skip)) return;

        if (mc.player.getMainHandStack().getItem() == Items.FILLED_MAP || mc.player.getOffHandStack().getItem() == Items.FILLED_MAP) return;

        if (pac.chatMessage().startsWith("/") || pac.chatMessage().startsWith(Managers.COMMAND.getPrefix())) return;

        modifiedMessage = pac.chatMessage();

        if (prefixCheck.getValue() && !prefixText.getValue().isEmpty()) {
            if (modifiedMessage.startsWith("!")) {
                modifiedMessage = modifiedMessage.substring(1);
                modifiedMessage = "!" + prefixText.getValue() + " " + modifiedMessage;
            } else {
                modifiedMessage = prefixText.getValue() + " " + modifiedMessage;
            }
        }

        if (appendCheck.getValue() && !appendText.getValue().isEmpty()) {
            modifiedMessage = modifiedMessage + " " + appendText.getValue();
        }

        if (autoGlobal.getValue()) {
            if (!modifiedMessage.startsWith("!")) {
                modifiedMessage = "!" + modifiedMessage;
            }
        }

        if (textType.getValue() == TextType.Custom) {
            modifiedMessage = modifiedMessage;
        }

        skip = modifiedMessage;
        mc.player.networkHandler.sendChatMessage(modifiedMessage);
        e.cancel();
    }
}
