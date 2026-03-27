package thunder.hack.features.modules.misc;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.text.Text;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.function.Consumer;

public class BotConnection {
    private final String name;
    private ClientConnection connection;
    private boolean connected;
    private Consumer<String> callback;

    public BotConnection(String name, Consumer<String> callback) {
        this.name = name;
        this.callback = callback;
    }

    public void connect(String host, int port) {
        new Thread(() -> {
            try {
                InetSocketAddress addr = new InetSocketAddress(host, port);
                connection = ClientConnection.connect(addr, false, null);
                connection.send(new HandshakeC2SPacket(host, port, net.minecraft.network.NetworkState.LOGIN));
                connection.send(new LoginHelloC2SPacket(name, UUID.randomUUID()));
                connected = true;
                callback.accept("§a[Bot] §7" + name + " §aconnected");
            } catch (Exception e) {
                callback.accept("§c[Bot] §7" + name + " §cfailed: " + e.getMessage());
            }
        }).start();
    }

    public void disconnect() {
        if (connection != null) {
            connection.disconnect(Text.literal("Disconnected"));
            connected = false;
        }
    }

    public boolean isConnected() { return connected; }
    public String getName() { return name; }
    public void sendChat(String msg) {}
}
