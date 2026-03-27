package thunder.hack.features.modules.misc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.text.Text;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.function.Consumer;

public class BotConnection {
    private final String name;
    private final UUID uuid;
    private ClientConnection connection;
    private boolean connected = false;
    private Consumer<String> chatCallback;

    public BotConnection(String name, Consumer<String> chatCallback) {
        this.name = name;
        this.uuid = UUID.randomUUID();
        this.chatCallback = chatCallback;
    }

    public String getName() { return name; }
    public boolean isConnected() { return connected; }

    public void connect(String host, int port) {
        if (connected) return;
        new Thread(() -> {
            try {
                InetSocketAddress address = new InetSocketAddress(host, port);
                connection = ClientConnection.connect(address, false, null);
                connection.send(new HandshakeC2SPacket(host, port, net.minecraft.network.NetworkState.LOGIN));
                connection.send(new LoginHelloC2SPacket(name, uuid));
                connected = true;
                chatCallback.accept("§a[Bot] §7" + name + " §aconnected");
            } catch (Exception e) {
                chatCallback.accept("§c[Bot] §7" + name + " §cfailed: " + e.getMessage());
            }
        }).start();
    }

    public void disconnect() {
        if (connection != null && connected) {
            connection.disconnect(Text.literal("Disconnected"));
            connected = false;
        }
    }

    public void update() {}

    public void sendChat(String message) {}
}
