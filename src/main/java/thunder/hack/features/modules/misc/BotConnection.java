package thunder.hack.features.modules.misc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.s2c.login.LoginHelloS2CPacket;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.function.Consumer;

public class BotConnection {
    private final String name;
    private final UUID uuid;
    private ClientConnection connection;
    private boolean connected = false;
    private boolean falling = false;
    private double x, y, z;
    private float yaw, pitch;
    private int tickCounter = 0;
    private int fallTicks = 0;
    private Consumer<String> chatCallback;

    public BotConnection(String name, Consumer<String> chatCallback) {
        this.name = name;
        this.uuid = UUID.randomUUID();
        this.chatCallback = chatCallback;
        this.x = 0;
        this.y = 256;
        this.z = 0;
        this.yaw = 0;
        this.pitch = 0;
    }

    public String getName() { return name; }
    public boolean isConnected() { return connected; }
    public boolean isFalling() { return falling; }

    public void connect(String host, int port) {
        if (connected) return;
        new Thread(() -> {
            try {
                InetSocketAddress address = new InetSocketAddress(host, port);
                connection = ClientConnection.connect(address, false, null);
                connection.setPacketListener(new BotPacketListener(this));
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

    public void update() {
        if (!connected) return;
        tickCounter++;
        if (falling) {
            fallTicks++;
            y -= 0.5;
            if (y < -64) {
                chatCallback.accept("§e[Bot] §7" + name + " §efell into void, reconnecting...");
                reconnect();
            }
        }
        if (tickCounter >= 20) {
            tickCounter = 0;
        }
    }

    private void reconnect() {
        if (connection != null) disconnect();
        String currentHost = getCurrentServerHost();
        int currentPort = getCurrentServerPort();
        if (currentHost != null) connect(currentHost, currentPort);
    }

    public void onTeleport(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.falling = false;
        this.fallTicks = 0;
    }

    public void onFall() { this.falling = true; }

    public void sendChat(String message) {}

    private String getCurrentServerHost() {
        if (MinecraftClient.getInstance().getCurrentServerEntry() != null) {
            String address = MinecraftClient.getInstance().getCurrentServerEntry().address;
            int colonIndex = address.indexOf(':');
            if (colonIndex != -1) return address.substring(0, colonIndex);
            return address;
        }
        return null;
    }

    private int getCurrentServerPort() {
        if (MinecraftClient.getInstance().getCurrentServerEntry() != null) {
            String address = MinecraftClient.getInstance().getCurrentServerEntry().address;
            int colonIndex = address.indexOf(':');
            if (colonIndex != -1) {
                try { return Integer.parseInt(address.substring(colonIndex + 1)); }
                catch (NumberFormatException e) { return 25565; }
            }
        }
        return 25565;
    }
}
