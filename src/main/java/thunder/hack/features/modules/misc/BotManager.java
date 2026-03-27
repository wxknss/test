package thunder.hack.features.modules.misc;

import net.minecraft.text.Text;
import thunder.hack.features.modules.Module;
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
        bots.values().forEach(BotConnection::disconnect);
        bots.clear();
    }

    public void addBot(String name) {
        if (bots.containsKey(name)) {
            sendMsg("§cBot §7" + name + " §calready exists");
            return;
        }
        String host = getHost();
        if (host == null) {
            sendMsg("§cNot connected to any server");
            return;
        }
        int port = getPort();
        BotConnection bot = new BotConnection(name, this::sendMsg);
        bot.connect(host, port);
        bots.put(name, bot);
        sendMsg("§aBot §7" + name + " §aconnecting to §7" + host + ":" + port);
    }

    public void removeBot(String name) {
        BotConnection bot = bots.remove(name);
        if (bot != null) {
            bot.disconnect();
            sendMsg("§cBot §7" + name + " §cdisconnected");
        } else {
            sendMsg("§cBot §7" + name + " §cnot found");
        }
    }

    public void sayAll(String message) {
        if (bots.isEmpty()) {
            sendMsg("§cNo bots connected");
            return;
        }
        for (BotConnection bot : bots.values()) {
            if (bot.isConnected()) bot.sendChat(message);
        }
        sendMsg("§aMessage sent to §7" + bots.size() + " §abots");
    }

    private String getHost() {
        if (mc.getCurrentServerEntry() == null) return null;
        String addr = mc.getCurrentServerEntry().address;
        int idx = addr.indexOf(':');
        return idx == -1 ? addr : addr.substring(0, idx);
    }

    private int getPort() {
        if (mc.getCurrentServerEntry() == null) return 25565;
        String addr = mc.getCurrentServerEntry().address;
        int idx = addr.indexOf(':');
        if (idx != -1) {
            try { return Integer.parseInt(addr.substring(idx + 1)); }
            catch (NumberFormatException e) { return 25565; }
        }
        return 25565;
    }

    private void sendMsg(String msg) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(msg), false);
    }
}
