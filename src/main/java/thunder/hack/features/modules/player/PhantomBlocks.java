package thunder.hack.features.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

import java.util.concurrent.ConcurrentHashMap;

public class PhantomBlocks extends Module {
    public PhantomBlocks() {
        super("PhantomBlocks", Category.PLAYER);
    }

    private final Setting<Boolean> persist = new Setting<>("Persist", true);
    private final Setting<Boolean> render = new Setting<>("Render", true);
    private final Setting<Integer> renderTime = new Setting<>("RenderTime", 5000, 1000, 30000, v -> render.getValue());

    private final ConcurrentHashMap<BlockPos, Long> phantomBlocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<BlockPos, Block> originalBlocks = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        phantomBlocks.clear();
        originalBlocks.clear();
    }

    @Override
    public void onDisable() {
        if (!persist.getValue()) {
            phantomBlocks.clear();
            originalBlocks.clear();
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (e.getPacket() instanceof BlockUpdateS2CPacket packet) {
            BlockPos pos = packet.getPos();
            Block block = packet.getState().getBlock();

            // Если сервер говорит, что блок стал воздухом (сломан)
            if (block == Blocks.AIR) {
                // Но у нас в кэше есть блок, который сервер не должен был ломать
                Block original = mc.world.getBlockState(pos).getBlock();
                if (original != Blocks.AIR && original != Blocks.BEDROCK) {
                    phantomBlocks.put(pos, System.currentTimeMillis());
                    originalBlocks.put(pos, original);
                    
                    // Восстанавливаем блок визуально
                    mc.world.setBlockState(pos, original.getDefaultState(), 0);
                }
            }
        }
    }

    @Override
    public void onUpdate() {
        if (render.getValue()) {
            long now = System.currentTimeMillis();
            phantomBlocks.entrySet().removeIf(entry -> {
                if (now - entry.getValue() > renderTime.getValue()) {
                    // Возвращаем реальный блок
                    BlockPos pos = entry.getKey();
                    mc.world.setBlockState(pos, mc.world.getBlockState(pos), 0);
                    return true;
                }
                return false;
            });
        }
    }

    // Проверка, является ли блок фантомным
    public static boolean isPhantomBlock(BlockPos pos) {
        return false; // Заглушка
    }
}
