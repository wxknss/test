package thunder.hack.features.modules.combat;

import io.netty.buffer.Unpooled;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.jetbrains.annotations.NotNull;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.injection.accesors.IClientPlayerEntity;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

public final class Criticals extends Module {
    public final Setting<Mode> mode = new Setting<>("Mode", Mode.UpdatedNCP);
    public final Setting<Integer> hurtTime = new Setting<>("HurtTime", 10, 0, 10);

    public static boolean cancelCrit;

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

            if (ent instanceof LivingEntity lent && lent.hurtTime > hurtTime.getValue()) return;

            doCrit();
        }
    }

    public void doCrit() {
        if (isDisabled() || mc.player == null || mc.world == null) return;

        if (!(mc.player.isOnGround() || mc.player.getAbilities().flying || mode.is(Mode.Grim)))
            return;

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

    public static Entity getEntity(PlayerInteractEntityC2SPacket packet) {
        InteractInfo info = readPacket(packet);
        if (info == null) return null;
        return mc.world.getEntityById(info.entityId);
    }

    public static InteractType getInteractType(PlayerInteractEntityC2SPacket packet) {
        InteractInfo info = readPacket(packet);
        if (info == null) return null;
        return info.type;
    }

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
        Ncp, Strict, OldNCP, UpdatedNCP, Grim
    }
}
