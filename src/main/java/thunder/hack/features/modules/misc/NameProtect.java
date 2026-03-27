package thunder.hack.features.modules.misc;

import net.minecraft.entity.player.PlayerEntity;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

public class NameProtect extends Module {
    public NameProtect() {
        super("NameProtect", Category.MISC);
    }

    // ===== НАСТРОЙКИ =====
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Default);
    private final Setting<String> customName = new Setting<>("CustomName", "Hell_Raider", v -> mode.is(Mode.Default));
    private final Setting<String> targetName = new Setting<>("TargetName", "player123", v -> mode.is(Mode.Fake));
    private final Setting<String> fakeName = new Setting<>("FakeName", "&cFakeNick", v -> mode.is(Mode.Fake));
    
    // ===== ПУБЛИЧНЫЕ СТАТИЧЕСКИЕ ПОЛЯ ДЛЯ СОВМЕСТИМОСТИ =====
    public static Setting<String> newName = new Setting<>("name", "Hell_Raider");
    public static Setting<Boolean> hideFriends = new Setting<>("Hide friends", true);

    public enum Mode {
        Default,     // Замена своего ника
        Fake,        // Замена чужого ника
        Obfuscated   // Искаженный текст
    }

    // Получение имени для отображения
    public static String getFormattedName(PlayerEntity player) {
        if (!ModuleManager.nameProtect.isEnabled()) {
            return player.getDisplayName().getString();
        }

        NameProtect np = ModuleManager.nameProtect;
        String playerName = player.getGameProfile().getName();
        boolean isSelf = player == mc.player;

        switch (np.mode.getValue()) {
            case Default:
                if (isSelf) {
                    return np.customName.getValue().replace("&", "§");
                }
                break;
                
            case Fake:
                String target = np.targetName.getValue();
                if (playerName.equalsIgnoreCase(target)) {
                    return np.fakeName.getValue().replace("&", "§");
                }
                break;
                
            case Obfuscated:
                String original = player.getDisplayName().getString();
                String clean = original.replaceAll("§[0-9a-fk-or]", "");
                return "§k" + clean + "§r";
        }

        return player.getDisplayName().getString();
    }
    
    // Получение собственного имени
    public static String getOwnName() {
        if (!ModuleManager.nameProtect.isEnabled()) {
            return mc.getGameProfile().getName();
        }
        NameProtect np = ModuleManager.nameProtect;
        if (np.mode.is(Mode.Default)) {
            return np.customName.getValue().replace("&", "§");
        }
        return mc.getGameProfile().getName();
    }
    
    // Для совместимости со старым кодом
    public static String getCustomName() {
        if (!ModuleManager.nameProtect.isEnabled()) {
            return mc.getGameProfile().getName();
        }
        return newName.getValue().replaceAll("&", "\u00a7");
    }
}
