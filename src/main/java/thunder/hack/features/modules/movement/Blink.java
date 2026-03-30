package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import thunder.hack.events.impl.EventTick;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.Bind;
import thunder.hack.setting.impl.ColorSetting;
import thunder.hack.utility.player.PlayerEntityCopy;
import thunder.hack.utility.render.Render3DEngine;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static thunder.hack.features.modules.client.ClientSettings.isRu;

public class Blink extends Module {
    public Blink() {
        super("Blink", Category.MOVEMENT);
    }

    private final Setting<Boolean> pulse = new Setting<>("Pulse", false);
    private final Setting<Boolean> autoDisable = new Setting<>("AutoDisable", false);
    private final Setting<Boolean> disableOnVelocity = new Setting<>("DisableOnVelocity", false);
    private final Setting<Integer> disablePackets = new Setting<>("DisablePackets", 17, 1, 1000, v -> autoDisable.getValue());
    private final Setting<Integer> pulsePackets = new Setting<>("PulsePackets", 20, 1, 1000, v -> pulse.getValue());
    private final Setting<Boolean> render = new Setting<>("Render", true);
    private final Setting<RenderMode> renderMode = new Setting<>("Render Mode", RenderMode.Circle, value -> render.getValue());
    private final Setting<ColorSetting> circleColor = new Setting<>("Color", new ColorSetting(0xFFda6464), value -> render.getValue() && renderMode.getValue() == RenderMode.Circle || renderMode.getValue() == RenderMode.Both);
    private final Setting<Bind> cancel = new Setting<>("Cancel", new Bind(GLFW.GLFW_KEY_LEFT_SHIFT, false, false));

    private enum RenderMode {
        Circle,
        Model,
        Both
    }

    private PlayerEntityCopy blinkPlayer;
    public static Vec3d lastPos = Vec3d.ZERO;
    private Vec3d prevVelocity = Vec3d.ZERO;
    private float prevYaw = 0;
    private boolean prevSprinting = false;
    private final Queue<Packet<?>> storedPackets = new LinkedList<>();
    private final Queue<Packet<?>> storedTransactions = new LinkedList<>();
    private final AtomicBoolean sending = new AtomicBoolean(false);
    private int packetCounter = 0;
    private boolean isBlinking = true;

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null || mc.isIntegratedServerRunning() || mc.getNetworkHandler() == null) {
            disable();
            return;
        }

        storedTransactions.clear();
        lastPos = mc.player.getPos();
        prevVelocity = mc.player.getVelocity();
        prevYaw = mc.player.getYaw();
        prevSprinting = mc.player.isSprinting();
        sending.set(false);
        storedPackets.clear();
        packetCounter = 0;
        isBlinking = true;
        
        if (blinkPlayer == null) {
            blinkPlayer = new PlayerEntityCopy();
            blinkPlayer.spawn();
        }
    }

    @Override
    public void onDisable() {
        if (mc.world == null || mc.player == null) return;

        // Отправляем все накопленные пакеты
        while (!storedPackets.isEmpty()) {
            sendPacket(storedPackets.poll());
        }

        if (blinkPlayer != null) {
            blinkPlayer.deSpawn();
            blinkPlayer = null;
        }
        
        isBlinking = false;
        packetCounter = 0;
    }

    @Override
    public String getDisplayInfo() {
        return Integer.toString(storedPackets.size());
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket vel && vel.getId() == mc.player.getId() && disableOnVelocity.getValue()) {
            disable(isRu() ? "Выключен из-за велосити!" : "Disabled due to velocity!");
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (fullNullCheck()) return;

        Packet<?> packet = event.getPacket();

        if (sending.get()) {
            return;
        }

        // Пропускаем важные пакеты
        if (packet instanceof CommonPongC2SPacket || packet instanceof KeepAliveC2SPacket) {
            storedTransactions.add(packet);
            return;
        }

        // Всегда пропускаем чат и команды
        if (packet instanceof ChatMessageC2SPacket || packet instanceof CommandExecutionC2SPacket) {
            return;
        }

        event.cancel();
        storedPackets.add(packet);
        packetCounter++;
        
        // Автовыключение по количеству пакетов
        if (autoDisable.getValue() && packetCounter >= disablePackets.getValue()) {
            disable(isRu() ? "Автовыключение!" : "Auto disabled!");
        }
        
        // Пульс: отправляем накопленные пакеты
        if (pulse.getValue() && packetCounter >= pulsePackets.getValue()) {
            sendPackets();
        }
    }

    @EventHandler
    public void onUpdate(EventTick event) {
        if (fullNullCheck()) return;

        // Кнопка отмены
        if (isKeyPressed(cancel)) {
            storedPackets.clear();
            mc.player.setPos(lastPos.getX(), lastPos.getY(), lastPos.getZ());
            mc.player.setVelocity(prevVelocity);
            mc.player.setYaw(prevYaw);
            mc.player.setSprinting(prevSprinting);
            mc.player.setSneaking(false);
            mc.options.sneakKey.setPressed(false);
            sending.set(true);
            while (!storedTransactions.isEmpty()) {
                sendPacket(storedTransactions.poll());
            }
            sending.set(false);
            disable(isRu() ? "Отменяю.." : "Canceling..");
            return;
        }

        // Обновляем позицию фантомного игрока
        if (blinkPlayer != null && lastPos != null) {
            blinkPlayer.updatePosition(lastPos.x, lastPos.y, lastPos.z);
        }
    }

    private void sendPackets() {
        if (mc.player == null) return;
        sending.set(true);

        // Отправляем все накопленные пакеты
        while (!storedPackets.isEmpty()) {
            Packet<?> packet = storedPackets.poll();
            sendPacket(packet);
            
            // Обновляем последнюю позицию из пакетов движения
            if (packet instanceof PlayerMoveC2SPacket movePacket) {
                lastPos = new Vec3d(
                    movePacket.getX(mc.player.getX()), 
                    movePacket.getY(mc.player.getY()), 
                    movePacket.getZ(mc.player.getZ())
                );
            }
        }

        sending.set(false);
        packetCounter = 0;
        
        // Обновляем фантомного игрока после отправки пакетов
        if (blinkPlayer != null && lastPos != null) {
            blinkPlayer.updatePosition(lastPos.x, lastPos.y, lastPos.z);
        }
    }

    public void onRender3D(MatrixStack stack) {
        if (!render.getValue() || lastPos == null) return;
        
        if (renderMode.getValue() == RenderMode.Circle || renderMode.getValue() == RenderMode.Both) {
            float[] hsb = Color.RGBtoHSB(circleColor.getValue().getRed(), circleColor.getValue().getGreen(), circleColor.getValue().getBlue(), null);
            float hue = (float) (System.currentTimeMillis() % 7200L) / 7200F;
            int rgb = Color.getHSBColor(hue, hsb[1], hsb[2]).getRGB();
            ArrayList<Vec3d> vecs = new ArrayList<>();
            double x = lastPos.x;
            double y = lastPos.y;
            double z = lastPos.z;

            for (int i = 0; i <= 360; ++i) {
                Vec3d vec = new Vec3d(x + Math.sin((double) i * Math.PI / 180.0) * 0.5D, y + 0.01, z + Math.cos((double) i * Math.PI / 180.0) * 0.5D);
                vecs.add(vec);
            }

            for (int j = 0; j < vecs.size() - 1; ++j) {
                Render3DEngine.drawLine(vecs.get(j), vecs.get(j + 1), new Color(rgb));
                hue += (1F / 360F);
                rgb = Color.getHSBColor(hue, hsb[1], hsb[2]).getRGB();
            }
        }
        
        if (renderMode.getValue() == RenderMode.Model || renderMode.getValue() == RenderMode.Both) {
            if (blinkPlayer == null) {
                blinkPlayer = new PlayerEntityCopy();
                blinkPlayer.spawn();
            }
            if (lastPos != null) {
                blinkPlayer.updatePosition(lastPos.x, lastPos.y, lastPos.z);
            }
        }
    }
}
