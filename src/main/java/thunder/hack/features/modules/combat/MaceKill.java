package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import thunder.hack.events.impl.EventAttack;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

public class MaceKill extends Module {
    public MaceKill() {
        super("MaceKill", Category.COMBAT);
    }

    private final Setting<Integer> fallHeight = new Setting<>("FallHeight", 22, 5, 100);
    private final Setting<Integer> teleportPackets = new Setting<>("TeleportPackets", 10, 2, 50);

    @EventHandler
    public void onAttack(EventAttack event) {
        if (fullNullCheck()) return;
        if (mc.player.getMainHandStack().getItem() != Items.MACE) return;

        double startX = mc.player.getX();
        double startY = mc.player.getY();
        double startZ = mc.player.getZ();
        double targetY = startY + fallHeight.getValue();

        for (int i = 0; i < teleportPackets.getValue(); i++) {
            sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(startX, startY, startZ, true));
        }

        mc.player.setPosition(startX, targetY, startZ);
        sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(startX, targetY, startZ, false));

        mc.player.fallDistance = fallHeight.getValue();

        mc.player.setPosition(startX, startY, startZ);
        sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(startX, startY, startZ, true));
    }

    @Override
    public String getDisplayInfo() {
        return fallHeight.getValue() + "m";
    }
}
