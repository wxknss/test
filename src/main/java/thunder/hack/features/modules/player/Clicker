package thunder.hack.features.modules.player;

import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

public class Clicker extends Module {
    public Clicker() {
        super("Clicker", Category.PLAYER);
    }

    private final Setting<Integer> cps = new Setting<>("CPS", 1000, 1, 5000);
    private final Setting<Boolean> onlyContainers = new Setting<>("OnlyContainers", true);
    private final Setting<Boolean> blockGui = new Setting<>("BlockGUI", true);

    private final Timer timer = new Timer();
    private boolean clicking = false;

    @Override
    public void onEnable() {
        clicking = true;
        timer.reset();
        displayMessage("§aClicker started §7(CPS: " + cps.getValue() + ")");
    }

    @Override
    public void onDisable() {
        clicking = false;
        displayMessage("§cClicker stopped");
    }

    @Override
    public void onUpdate() {
        if (!clicking || fullNullCheck()) return;

        if (blockGui.getValue() && mc.currentScreen != null) {
            return;
        }

        if (onlyContainers.getValue() && !isLookingAtContainer()) {
            return;
        }

        long delay = 1000 / cps.getValue();
        if (!timer.passedMs(delay)) return;

        clickPacket();
        timer.reset();
    }

    private boolean isLookingAtContainer() {
        if (mc.crosshairTarget instanceof BlockHitResult hit) {
            BlockPos pos = hit.getBlockPos();
            Block block = mc.world.getBlockState(pos).getBlock();
            return block instanceof ChestBlock || 
                   block instanceof TrappedChestBlock || 
                   block instanceof ShulkerBoxBlock;
        }
        return false;
    }

    private void clickPacket() {
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        
        // Отправляем пакет взаимодействия с блоком
        sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit));
        
        // Имитируем swing руки (для визуала)
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void displayMessage(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(msg), false);
        }
    }
}
