package thunder.hack.features.modules.misc;

import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

public class NameProtect extends Module {
    public NameProtect() {
        super("NameProtect", Category.MISC);
    }

    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Default);
    private final Setting<String> ownName = new Setting<>("OwnName", "Hell_Raider", v -> mode.is(Mode.Default) || mode.is(Mode.Obfuscated));
    private final Setting<String> targetName = new Setting<>("TargetName", "player123", v -> mode.is(Mode.Anal));
    private final Setting<String> fakeName = new Setting<>("FakeName", "&cFakeNick", v -> mode.is(Mode.Anal));
    private final Setting<Boolean> hideFriends = new Setting<>("HideFriends", true);

    public enum Mode {
        Default, Anal, Obfuscated
    }

    public static Setting<String> newName = new Setting<>("name", "Hell_Raider");
    public static Setting<Boolean> hideFriendsStatic = new Setting<>("Hide friends", true);

    public static String getFormattedName(net.minecraft.entity.player.PlayerEntity player) {
        if (!ModuleManager.nameProtect.isEnabled()) {
            return player.getDisplayName().getString();
        }

        NameProtect np = ModuleManager.nameProtect;
        String realName = player.getGameProfile().getName();
        boolean isSelf = player == mc.player;

        switch (np.mode.getValue()) {
            case Default:
                if (isSelf) {
                    return np.ownName.getValue().replace("&", "§");
                }
                break;

            case Anal:
                String target = np.targetName.getValue();
                if (realName.equalsIgnoreCase(target) || player.getDisplayName().getString().contains(target)) {
                    return np.fakeName.getValue().replace("&", "§");
                }
                break;

            case Obfuscated:
                if (isSelf) {
                    String original = player.getDisplayName().getString();
                    String colorCode = "";
                    for (int i = 0; i < original.length() - 1; i++) {
                        if (original.charAt(i) == '§') {
                            colorCode = original.substring(i, i + 2);
                            break;
                        }
                    }
                    String clean = original.replaceAll("§[0-9a-fk-or]", "");
                    return colorCode + "§k" + clean + "§r";
                }
                break;
        }

        return player.getDisplayName().getString();
    }

    public static String getCustomName() {
        if (!ModuleManager.nameProtect.isEnabled()) {
            return mc.getGameProfile().getName();
        }
        NameProtect np = ModuleManager.nameProtect;
        if (np.mode.is(Mode.Default) || np.mode.is(Mode.Obfuscated)) {
            return np.ownName.getValue().replace("&", "§");
        }
        return newName.getValue().replaceAll("&", "§");
    }

    public static boolean hideFriends() {
        return ModuleManager.nameProtect.isEnabled() && ModuleManager.nameProtect.hideFriends.getValue();
    }
}
