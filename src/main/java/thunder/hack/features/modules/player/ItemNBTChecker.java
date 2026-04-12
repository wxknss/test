package thunder.hack.features.modules.player;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ItemNBTChecker extends Module {
    public ItemNBTChecker() {
        super("ItemNBTChecker", Category.PLAYER);
    }

    private final Setting<Integer> radius = new Setting<>("Radius", 5, 1, 10);
    private final Setting<Integer> scanDelay = new Setting<>("ScanDelay", 2, 1, 10);
    private final Setting<Boolean> notifyInChat = new Setting<>("NotifyInChat", true);
    private final Setting<Boolean> highlightInWorld = new Setting<>("HighlightInWorld", true);
    private final Setting<String> searchNBT = new Setting<>("SearchNBT", "money");

    private final Timer timer = new Timer();
    private ItemEntity lastNotifiedItem = null;

    @Override
    public void onUpdate() {
        if (fullNullCheck()) return;
        if (!timer.passedMs(scanDelay.getValue() * 1000L)) return;

        List<ItemEntity> items = mc.world.getEntities()
                .stream()
                .filter(e -> e instanceof ItemEntity)
                .map(e -> (ItemEntity) e)
                .filter(e -> mc.player.distanceTo(e) <= radius.getValue())
                .sorted(Comparator.comparingDouble(e -> mc.player.distanceTo(e)))
                .limit(5)
                .collect(Collectors.toList());

        for (ItemEntity item : items) {
            ItemStack stack = item.getStack();
            NbtCompound nbt = stack.getNbt();

            if (nbt != null && !nbt.isEmpty()) {
                String nbtString = nbt.asString();
                
                if (nbtString.toLowerCase().contains(searchNBT.getValue().toLowerCase())) {
                    if (notifyInChat.getValue() && (lastNotifiedItem == null || lastNotifiedItem != item)) {
                        String itemName = stack.getName().getString();
                        sendMessage(Formatting.GREEN + "[+] " + Formatting.WHITE + "Found " + Formatting.GOLD + itemName + 
                                   Formatting.WHITE + " with NBT containing: " + Formatting.YELLOW + searchNBT.getValue());
                        
                        if (nbtString.length() < 200) {
                            mc.player.sendMessage(Text.literal(Formatting.GRAY + "  NBT: " + nbtString), false);
                        }
                        
                        lastNotifiedItem = item;
                    }
                    
                    if (highlightInWorld.getValue()) {
                        // Добавляем подсветку через Outline (если есть Render3DEngine)
                        // Или просто спамим в чат, зависит от твоего рендера
                    }
                    
                    break;
                }
            }
        }
        
        timer.reset();
    }
    
    @Override
    public void onDisable() {
        lastNotifiedItem = null;
    }
    
    @Override
    public String getDisplayInfo() {
        return radius.getValue() + "m";
    }
}
