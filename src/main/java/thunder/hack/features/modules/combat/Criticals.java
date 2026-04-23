package thunder.hack.features.modules.combat;

import io.netty.buffer.Unpooled;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.injection.accesors.IClientPlayerEntity;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

public final class Criticals extends Module {
    public final Setting<Mode> mode = new Setting<>("Mode", Mode.UpdatedNCP);
    public final Setting<Integer> critDelay = new Setting<>("CritDelay", 0, 0, 1000); // задержка в мс, 0 = без ограничения

    private boolean cancelCrit;
    private final Timer critTimer = new Timer();

    public Criticals() {
        super("Criticals", Category.COMBAT);
    }

    @EventHandler
    public void onPacketSend(PacketEvent.@NotNull Send event) {
        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket packet) {
            InteractInfo info = readPacket(packet);
            if (info == null || info.type != InteractType.ATTACK) return;

            Entity ent = mc.world.getEntityById(info.entityId);
            if (ent == null || ent instanceof EndCrystalEntity || cancelCrit) return;

            // Ограничение частоты
            if (!critTimer.passedMs(critDelay.getValue())) return;
            critTimer.reset();

            doCrit();
        }
    }

    public void doCrit() {
        if (isDisabled() || mc.player == null || mc.world == null) return;

        // Условия для срабатывания: на земле, в полёте, или Grim/Matrix (донатный флай)
        if (!(mc.player.isOnGround() || mc.player.getAbilities().flying || mode.is(Mode.Grim) || mode.is(Mode.Vanilla)))
            return;

        // Отключаем криты в лаве/воде, чтобы не светиться
        if (mc.player.isInLava() || mc.player.isSubmergedInWater()) return;

        switch (mode.getValue()) {
            case OldNCP -> {
                sendCritPacket(0.00001058293536, false);
                sendCritPacket(0.00000916580235, false);
                sendCritPacket(0.00000010371854, false);
            }
            case Ncp -> {
                sendCritPacket(0.0625D, false);
                sendCritPacket(0.0, false);
            }
            case UpdatedNCP -> {
                sendCritPacket(0.000000271875, false);
                sendCritPacket(0.0, false);
            }
            case Strict -> {
                sendCritPacket(0.062600301692775, false);
                sendCritPacket(0.07260029960661, false);
                sendCritPacket(0.0, false);
                sendCritPacket(0.0, false);
            }
            case Grim -> {
                if (!mc.player.isOnGround())
                    sendCritPacket(-0.000001, true);
            }
            case Vanilla -> {
                // Всегда критуем, даже при прыжке вверх
                // Отправляем микро-снижение, чтобы имитировать падение
                sendCritPacket(-0.0001, false);
            }
        }
    }

    private void sendCritPacket(double yDelta, boolean full) {
        double x = mc.player.getX();
        double y = mc.player.getY() + yDelta;
        double z = mc.player.getZ();
        if (!full) {
            sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false));
        } else {
            sendPacket(new PlayerMoveC2SPacket.Full(x, y, z,
                    ((IClientPlayerEntity) mc.player).getLastYaw(),
                    ((IClientPlayerEntity) mc.player).getLastPitch(),
                    false));
        }
    }

    /**
     * Читает из пакета ID сущности и тип взаимодействия.
     * Исправляет утечку ByteBuf.
     */
    private static InteractInfo readPacket(PlayerInteractEntityC2SPacket packet) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            packet.write(buf);
            int entityId = buf.readVarInt();
            InteractType type = buf.readEnumConstant(InteractType.class);
            return new InteractInfo(entityId, type);
        } catch (Exception e) {
            return null;
        } finally {
            buf.release();
        }
    }

    private record InteractInfo(int entityId, InteractType type) {}

    public enum InteractType {
        INTERACT, ATTACK, INTERACT_AT
    }

    public enum Mode {
        Ncp, Strict, OldNCP, UpdatedNCP, Grim, Vanilla
    }

    // Управление отключением критов извне (для AutoCrystal и т.п.)
    public void setCancelCrit(boolean cancel) {
        this.cancelCrit = cancel;
    }

    public boolean isCancelCrit() {
        return cancelCrit;
    }
}
