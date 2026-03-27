package thunder.hack.features.modules.misc;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

public class NameProtect extends Module {
    public NameProtect() {
        super("NameProtect", Category.MISC);
    }

    // ===== НАСТРОЙКИ =====
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Default);
    private final Setting<String> customName = new Setting<>("CustomName", "JeNro0", v -> mode.is(Mode.Default));
    private final Setting<Boolean> hideFriends = new Setting<>("HideFriends", true);
    private final Setting<String> targetName = new Setting<>("TargetName", "player123", v -> mode.is(Mode.Fake));
    private final Setting<String> fakeName = new Setting<>("FakeName", "FakeNick", v -> mode.is(Mode.Fake));
    private final Setting<Boolean> keepColor = new Setting<>("KeepColor", true, v -> mode.is(Mode.Fake) || mode.is(Mode.Obfuscated));

    public enum Mode {
        Default,     // Замена своего ника
        Fake,        // Замена чужого ника
        Obfuscated   // Цензура через &k
    }

    // Получение имени для отображения
    public static String getFormattedName(PlayerEntity player) {
        if (!ModuleManager.nameProtect.isEnabled()) {
            return player.getDisplayName().getString();
        }

        NameProtect np = ModuleManager.nameProtect;
        String playerName = player.getDisplayName().getString();
        String cleanName = player.getGameProfile().getName();

        switch (np.mode.getValue()) {
            case Default:
                // Замена своего ника
                if (player == mc.player) {
                    return np.customName.getValue().replaceAll("&", "\u00a7");
                }
                break;

            case Fake:
                // Замена чужого ника
                String target = np.targetName.getValue();
                if (cleanName.equalsIgnoreCase(target) || playerName.contains(target)) {
                    return np.fakeName.getValue().replaceAll("&", "\u00a7");
                }
                break;

            case Obfuscated:
                // Цензура через &k
                String original = player.getDisplayName().getString();
                if (np.keepColor.getValue()) {
                    // Сохраняем цвет, если есть
                    String colorCode = getColorCode(original);
                    if (colorCode != null) {
                        return colorCode + "\u00a7k" + getObfuscatedText(original) + "\u00a7r";
                    }
                }
                return "\u00a7k" + getObfuscatedText(original) + "\u00a7r";
        }

        return player.getDisplayName().getString();
    }

    // Получение имени для себя (в чате, табе)
    public static String getOwnName() {
        if (!ModuleManager.nameProtect.isEnabled()) {
            return mc.getGameProfile().getName();
        }

        NameProtect np = ModuleManager.nameProtect;
        if (np.mode.is(Mode.Default)) {
            return np.customName.getValue().replaceAll("&", "\u00a7");
        }
        return mc.getGameProfile().getName();
    }

    // Получение цветового кода из текста
    private static String getColorCode(String text) {
        for (int i = 0; i < text.length() - 1; i++) {
            char c = text.charAt(i);
            if (c == '§' || c == '\u00a7') {
                return text.substring(i, i + 2);
            }
        }
        return null;
    }

    // Искажение текста (каждый символ заменяется на случайный)
    private static String getObfuscatedText(String text) {
        StringBuilder result = new StringBuilder();
        String cleanText = text.replaceAll("[§\u00a7][0-9a-fk-or]", "");
        for (char c : cleanText.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                result.append(c);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    // Для совместимости со старым кодом (NameTags)
    public static String getCustomName() {
        return getOwnName();
    }

    public static boolean hideFriends() {
        return ModuleManager.nameProtect.isEnabled() && ModuleManager.nameProtect.hideFriends.getValue();
    }
}
