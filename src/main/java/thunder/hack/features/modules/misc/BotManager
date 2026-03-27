package thunder.hack.features.modules.misc;

import net.minecraft.text.Text;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import java.util.concurrent.ConcurrentHashMap;

public class BotManager extends Module {
    private static BotManager instance;
    private final ConcurrentHashMap<String, BotConnection> bots = new ConcurrentHashMap<>();

    public BotManager() {
        super("BotManager", Category.MISC);
        instance = this;
    }

    public static BotManager getInstance() { return instance; }

    @Override
    public void onDisable() {
        for (BotConnection bot : bots.values()) bot.disconnect();
        bots.clear();
    }

    public void addBot(String name) {
        if (bots.containsKey(name)) {
            sendMessage("§cBot §7" + name + " §calready exists");
            return;
        }
        String host = getCurrentServerHost();
        int port = getCurrentServerPort();
        if (host == null) {
            sendMessage("§cNot connected to any server");
            return;
        }
        BotConnection bot = new BotConnection(name, this::sendMessage);
        bot.connect(host, port);
        bots.put(name, bot);
        sendMessage("§aBot §7" + name + " §aconnecting to §7" + host + ":" + port);
    }

    public void removeBot(String name) {
        BotConnection bot = bots.remove(name);
        if (bot != null) {
            bot.disconnect();
            sendMessage("§cBot §7" + name + " §cdisconnected");
        } else {
            sendMessage("§cBot §7" + name + " §cnot found");
        }
    }

    public void sayAll(String message) {
        if (bots.isEmpty()) {
            sendMessage("§cNo bots connected");
            return;
        }
        for (BotConnection bot : bots.values()) {
            if (bot.isConnected()) bot.sendChat(message);
        }
        sendMessage("§aMessage sent to §7" + bots.size() + " §abots");
    }

    @Override
    public void onUpdate() {
        for (BotConnection bot : bots.values()) bot.update();
    }

    private String getCurrentServerHost() {
        if (mc.getCurrentServerEntry() != null) {
            String address = mc.getCurrentServerEntry().address;
            int colonIndex = address.indexOf(':');
            if (colonIndex != -1) return address.substring(0, colonIndex);
            return address;
        }
        return null;
    }

    private int getCurrentServerPort() {
        if (mc.getCurrentServerEntry() != null) {
            String address = mc.getCurrentServerEntry().address;
            int colonIndex = address.indexOf(':');
            if (colonIndex != -1) {
                try { return Integer.parseInt(address.substring(colonIndex + 1)); }
                catch (NumberFormatException e) { return 25565; }
            }
        }
        return 25565;
    }

    private void sendMessage(String msg) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(msg), false);
    }
}
