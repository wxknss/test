package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import thunder.hack.ThunderHack;
import thunder.hack.events.impl.EventMove;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

import java.util.ArrayList;
import java.util.List;

public class AntiVoid extends Module {
    public AntiVoid() {
        super("AntiVoid", Category.MOVEMENT);
    }

    private final Setting<Mode> mode = new Setting<>("Mode", Mode.NCP);
    private final Setting<Float> minFallDistance = new Setting<>("MinFallDistance", 3.0f, 1.0f, 15.0f, v -> mode.is(Mode.NCP));
    private final Setting<Integer> pullbackDelay = new Setting<>("PullbackDelay", 1500, 500, 3000, v -> mode.is(Mode.NCP));
    private final Setting<Boolean> useTimer = new Setting<>("UseTimer", false, v -> mode.is(Mode.Timer));

    private final Timer pullbackTimer = new Timer();
    private final List<PlayerMoveC2SPacket> packetBuffer = new ArrayList<>();
    private double[] lastGroundPos = new double[3];
    private boolean timerFlag = false;

    private enum Mode { NCP, Timer }

    @Override
    public void onDisable() {
        releasePackets();
        if (timerFlag) ThunderHack.TICK_TIMER = 1f;
        timerFlag = false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(EventMove e) {
        if (fullNullCheck()) return;

        if (mode.is(Mode.Timer)) {
            if (fallingToVoid()) {
                ThunderHack.TICK_TIMER = 0.2f;
                timerFlag = true;
            } else if (timerFlag) {
                ThunderHack.TICK_TIMER = 1f;
                timerFlag = false;
            }
            return;
        }

        // Обновление безопасной позиции
        if (mc.player.isOnGround() || !fallingToVoid()) {
            lastGroundPos[0] = mc.player.getX();
            lastGroundPos[1] = mc.player.getY();
            lastGroundPos[2] = mc.player.getZ();
            releasePackets(); // сразу выпускаем буфер при безопасном положении
            pullbackTimer.reset();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPacketSend(PacketEvent.Send event) {
        if (fullNullCheck() || !mode.is(Mode.NCP)) return;

        if (event.getPacket() instanceof PlayerMoveC2SPacket packet) {
            // Проверяем, действительно ли мы падаем (набрали fallDistance) и ниже безопасной позиции
            boolean shouldBuffer = fallingToVoid()
                    && mc.player.fallDistance > minFallDistance.getValue()
                    && mc.player.getY() < lastGroundPos[1] - 1.0; // ниже безопасной позиции хотя бы на 1 блок

            if (shouldBuffer) {
                event.cancel();
                packetBuffer.add(packet);

                if (pullbackTimer.passedMs(pullbackDelay.getValue())) {
                    // Отправляем спасительный пакет
                    sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                            lastGroundPos[0], lastGroundPos[1] - 0.5, lastGroundPos[2], true));
                    releasePackets();
                    pullbackTimer.reset();
                }
            } else {
                // Вне пустоты или недостаточное падение – передаём пакеты как обычно
                releasePackets();
            }
        }
    }

    private void releasePackets() {
        for (PlayerMoveC2SPacket p : packetBuffer) {
            sendPacket(p);
        }
        packetBuffer.clear();
    }

    private boolean fallingToVoid() {
        for (int i = (int) mc.player.getY(); i >= -64; i--) {
            BlockPos pos = BlockPos.ofFloored(mc.player.getX(), i, mc.player.getZ());
            if (!mc.world.isAir(pos) && !mc.world.getBlockState(pos).isReplaceable()) {
                return false;
            }
        }
        return mc.player.fallDistance > 0;
    }
}
