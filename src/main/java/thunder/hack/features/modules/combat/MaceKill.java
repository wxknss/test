package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import thunder.hack.events.impl.EventAttack;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

public class MaceKill extends Module {
    public MaceKill() {
        super("MaceKill", Category.COMBAT);
    }

    private final Setting<Integer> fallHeight = new Setting<>("FallHeight", 22, 1, 170);
    private final Setting<Integer> teleportPackets = new Setting<>("TeleportPackets", 2, 1, 10);

    private boolean isEnabledCache = false;

    @Override
    public void onEnable() {
        isEnabledCache = true;
    }

    @Override
    public void onDisable() {
        isEnabledCache = false;
    }

    @EventHandler
    public void onAttack(EventAttack event) {
        if (!isEnabled()) return;
        if (fullNullCheck()) return;

        // Проверяем, держим ли мы булаву
        if (mc.player.getMainHandStack().getItem() != Items.MACE) return;

        int height = determineHeight();

        if (height == 0) return;

        // Teleport exploit для больших высот
        if (height > 10) {
            int packets = (int) Math.ceil(Math.abs(height / 10.0));
            for (int i = 0; i < packets; i++) {
                sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false));
            }
        } else {
            // Делаем минимум 2 раза для нейтрализации горизонтального расстояния
            for (int i = 0; i < teleportPackets.getValue(); i++) {
                sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.isOnGround()));
            }
        }

        // Телепортируемся на рассчитанную высоту
        sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            mc.player.getX(),
            mc.player.getY() + height,
            mc.player.getZ(),
            false
        ));

        // Возвращаемся на землю
        sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            mc.player.getX(),
            mc.player.getY(),
            mc.player.getZ(),
            false
        ));
    }

    private int determineHeight() {
        Box boundingBox = mc.player.getBoundingBox();

        for (int i = fallHeight.getValue(); i >= 1; i--) {
            Box newBoundingBox = boundingBox.offset(0, i, 0);

            // Проверяем, нет ли коллизий с блоками
            if (mc.world.getBlockCollisions(mc.player, newBoundingBox).iterator().hasNext()) {
                continue;
            }
            return i;
        }

        return 0;
    }

    @Override
    public String getDisplayInfo() {
        return fallHeight.getValue() + "m";
    }
}
