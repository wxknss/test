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
    private final Setting<Integer> fallTicks = new Setting<>("FallTicks", 8, 1, 30);
    private final Setting<Boolean> autoDisable = new Setting<>("AutoDisable", true);

    private boolean active;
    private int tickCounter;
    private double startY;
    private boolean hasTeleported;

    @Override
    public void onEnable() {
        active = false;
        tickCounter = 0;
        hasTeleported = false;
    }

    @EventHandler
    public void onAttack(EventAttack event) {
        if (fullNullCheck()) return;
        if (mc.player.getMainHandStack().getItem() != Items.MACE) return;
        if (active) return;

        startY = mc.player.getY();
        active = true;
        tickCounter = 0;
        hasTeleported = false;
    }

    @Override
    public void onUpdate() {
        if (!active) return;

        if (!hasTeleported) {
            double targetY = startY + fallHeight.getValue();

            for (int i = 0; i < teleportPackets.getValue(); i++) {
                sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), startY, mc.player.getZ(), true
                ));
            }

            sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), targetY, mc.player.getZ(), false
            ));

            for (int i = 0; i < 3; i++) {
                sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), targetY, mc.player.getZ(), false
                ));
            }

            mc.player.setPosition(mc.player.getX(), targetY, mc.player.getZ());
            hasTeleported = true;
            tickCounter = 0;
            return;
        }

        tickCounter++;

        if (tickCounter < fallTicks.getValue()) {
            double airY = startY + fallHeight.getValue();
            sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), airY, mc.player.getZ(), false
            ));
            mc.player.setPosition(mc.player.getX(), airY, mc.player.getZ());
            mc.player.fallDistance = tickCounter * 5.0f;
            return;
        }

        if (tickCounter == fallTicks.getValue()) {
            mc.player.fallDistance = fallHeight.getValue() * 3.5f;

            sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), startY, mc.player.getZ(), true
            ));
            mc.player.setPosition(mc.player.getX(), startY, mc.player.getZ());
            return;
        }

        if (tickCounter > fallTicks.getValue()) {
            sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), startY, mc.player.getZ(), true
            ));
            mc.player.setPosition(mc.player.getX(), startY, mc.player.getZ());
            active = false;
            hasTeleported = false;
            tickCounter = 0;

            if (autoDisable.getValue()) {
                setEnabled(false);
            }
        }
    }

    @Override
    public String getDisplayInfo() {
        return fallHeight.getValue() + "m";
    }
}
