package thunder.hack.features.modules.misc;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.ClientLoginPacketListener;
import net.minecraft.network.packet.s2c.login.LoginDisconnectS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginHelloS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.text.Text;

public class BotPacketListener implements ClientLoginPacketListener {
    private final BotConnection bot;
    private ClientConnection connection;

    public BotPacketListener(BotConnection bot) {
        this.bot = bot;
    }

    @Override
    public void onHello(LoginHelloS2CPacket packet) {}

    @Override
    public void onSuccess(LoginSuccessS2CPacket packet) {}

    @Override
    public void onDisconnect(LoginDisconnectS2CPacket packet) {
        bot.disconnect();
    }

    @Override
    public ClientConnection getConnection() {
        return connection;
    }

    @Override
    public void onDisconnected(Text reason) {
        bot.disconnect();
    }

    @Override
    public boolean isConnectionOpen() {
        return bot.isConnected();
    }
}
