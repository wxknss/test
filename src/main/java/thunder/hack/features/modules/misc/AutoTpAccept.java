package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import thunder.hack.core.Managers;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.gui.font.FontRenderers;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.client.HudEditor;
import thunder.hack.setting.Setting;
import thunder.hack.utility.ThunderUtility;
import thunder.hack.utility.math.MathUtility;

import static thunder.hack.features.modules.client.ClientSettings.isRu;

public class AutoTpAccept extends Module {
    public AutoTpAccept() {
        super("AutoTPaccept", Category.MISC);
    }

    public Setting<Boolean> grief = new Setting<>("Grief", false);
    public Setting<Boolean> onlyFriends = new Setting<>("onlyFriends", true);
    public Setting<Boolean> duo = new Setting<>("Duo", false);
    private final Setting<Integer> timeOut = new Setting<>("TimeOut", 60, 1, 180, v -> duo.getValue());
    private final Setting<Float> enemyRange = new Setting<>("EnemyRange", 10f, 1f, 50f, v -> duo.getValue());

    private TpTask tpTask;
    private String lastRequester;

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (fullNullCheck()) return;
        if (event.getPacket() instanceof GameMessageS2CPacket) {
            final GameMessageS2CPacket packet = event.getPacket();
            String message = packet.content().getString();
            
            if (message.contains("телепортироваться") || message.contains("tpaccept")) {
                String requester = ThunderUtility.solveName(message);
                if (requester == null || requester.equals("FATAL ERROR")) return;
                
                if (onlyFriends.getValue() && !Managers.FRIEND.isFriend(requester)) {
                    return;
                }
                
                lastRequester = requester;
                
                if (!duo.getValue()) {
                    acceptRequest();
                } else {
                    tpTask = new TpTask(() -> acceptRequest(), System.currentTimeMillis(), requester);
                    sendMessage(isRu() ? "Жду врага чтобы принять тп от " + requester : "Waiting for enemy to accept tp from " + requester);
                }
            }
        }
    }

    public void onRender2D(DrawContext context) {
        if (duo.getValue() && tpTask != null) {
            String text = (isRu() ? "Ждем врага рядом | " : "Waiting for enemy nearby | ") + 
                         MathUtility.round((timeOut.getValue() * 1000 - (System.currentTimeMillis() - tpTask.time())) / 1000f, 1) + "s";
            FontRenderers.sf_bold.drawCenteredString(context.getMatrices(), text, 
                mc.getWindow().getScaledWidth() / 2f, mc.getWindow().getScaledHeight() / 2f + 30, 
                HudEditor.getColor(1).getRGB());
        }
    }

    @Override
    public void onUpdate() {
        if (duo.getValue() && tpTask != null) {
            if (System.currentTimeMillis() - tpTask.time() > timeOut.getValue() * 1000L) {
                sendMessage(isRu() ? "Время вышло, тп не принят" : "Timeout, tp not accepted");
                tpTask = null;
                lastRequester = null;
                return;
            }
            
            PlayerEntity nearestEnemy = getNearestEnemy();
            if (nearestEnemy != null) {
                sendMessage(isRu() ? "Найден враг " + nearestEnemy.getName().getString() + ", принимаю тп от " + tpTask.requester() : 
                                   "Found enemy " + nearestEnemy.getName().getString() + ", accepting tp from " + tpTask.requester());
                tpTask.task().run();
                tpTask = null;
                lastRequester = null;
            }
        }
    }
    
    private PlayerEntity getNearestEnemy() {
        if (mc.world == null) return null;
        
        PlayerEntity nearest = null;
        double nearestDistance = enemyRange.getValue();
        
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (Managers.FRIEND.isFriend(player)) continue;
            
            double distance = mc.player.distanceTo(player);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        
        return nearest;
    }

    public void acceptRequest() {
        if (grief.getValue() && lastRequester != null) {
            mc.getNetworkHandler().sendChatCommand("tpaccept " + lastRequester);
        } else {
            mc.getNetworkHandler().sendChatCommand("tpaccept");
        }
        sendMessage(isRu() ? "Принят тп от " + lastRequester : "Accepted tp from " + lastRequester);
    }

    private record TpTask(Runnable task, long time, String requester) {}
}
