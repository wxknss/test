package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import thunder.hack.core.Managers;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.injection.accesors.IGameMessageS2CPacket;
import thunder.hack.gui.notification.Notification;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

import java.text.SimpleDateFormat;
import java.util.*;

import static thunder.hack.features.modules.client.ClientSettings.isRu;

public class ChatUtils extends Module {
    private final Setting<Welcomer> welcomer = new Setting<>("Welcomer", Welcomer.Off);
    private final Setting<Prefix> prefix = new Setting<>("Prefix", Prefix.None);
    private final Setting<Boolean> time = new Setting<>("Time", false);
    private final Setting<TimeColor> timeColor = new Setting<>("TimeColor", TimeColor.Gray, v -> time.getValue());
    private final Setting<BracketColor> bracketColor = new Setting<>("BracketColor", BracketColor.Gray, v -> time.getValue());
    private final Setting<CopyButton> copyButton = new Setting<>("CopyButton", CopyButton.Off);
    private final Setting<CopySymbol> copySymbol = new Setting<>("CopySymbol", CopySymbol.Heart, v -> copyButton.getValue() != CopyButton.Off);
    private final Setting<CopyColor> copyColor = new Setting<>("CopyColor", CopyColor.Red, v -> copyButton.getValue() != CopyButton.Off);
    private final Setting<Boolean> mention = new Setting<>("Mention", false);
    private final Setting<PMSound> pmSound = new Setting<>("PMSound", PMSound.Default);
    private final Setting<Boolean> antiBwFilter = new Setting<>("AntiBWFilter", false);
    private final Setting<Boolean> customFont = new Setting<>("CustomFont", false);
    private final Setting<Boolean> zov = new Setting<>("ZOV", false);
    private final Setting<Boolean> wavy = new Setting<>("wAvY", false);
    private final Setting<Boolean> translit = new Setting<>("Translit", false);
    private final Setting<Boolean> antiCoordLeak = new Setting<>("AntiCoordLeak", false);

    private final Timer timer = new Timer();
    private final Timer antiSpam = new Timer();
    private final Timer messageTimer = new Timer();
    private final LinkedHashMap<UUID, String> nameMap = new LinkedHashMap<>();
    private String skip;

    Map<String, String> ruToEng = Map.ofEntries(
            Map.entry("а", "a"),
            Map.entry("б", "6"),
            Map.entry("в", "B"),
            Map.entry("г", "r"),
            Map.entry("д", "d"),
            Map.entry("е", "e"),
            Map.entry("ё", "e"),
            Map.entry("ж", ">I<"),
            Map.entry("з", "3"),
            Map.entry("и", "u"),
            Map.entry("й", "u"),
            Map.entry("к", "k"),
            Map.entry("л", "JI"),
            Map.entry("м", "m"),
            Map.entry("н", "H"),
            Map.entry("о", "o"),
            Map.entry("п", "n"),
            Map.entry("р", "p"),
            Map.entry("с", "c"),
            Map.entry("т", "T"),
            Map.entry("у", "y"),
            Map.entry("ф", "f"),
            Map.entry("х", "x"),
            Map.entry("ц", "lI"),
            Map.entry("ч", "4"),
            Map.entry("ш", "w"),
            Map.entry("щ", "w"),
            Map.entry("ь", "b"),
            Map.entry("ы", "bI"),
            Map.entry("ъ", "b"),
            Map.entry("э", "-)"),
            Map.entry("ю", "I-O"),
            Map.entry("я", "9I")
    );
    
    Map<String, String> customFontMap = Map.ofEntries(
            Map.entry("а", "ᴀ"),
            Map.entry("б", "б"),
            Map.entry("в", "в"),
            Map.entry("г", "ᴦ"),
            Map.entry("д", "д"),
            Map.entry("е", "ᴇ"),
            Map.entry("ё", "ё"),
            Map.entry("ж", "ж"),
            Map.entry("з", "з"),
            Map.entry("и", "ᴎ"),
            Map.entry("й", "й"),
            Map.entry("к", "ᴋ"),
            Map.entry("л", "ᴫ"),
            Map.entry("м", "ᴍ"),
            Map.entry("н", "н"),
            Map.entry("о", "ᴏ"),
            Map.entry("п", "ᴨ"),
            Map.entry("р", "ᴩ"),
            Map.entry("с", "ᴄ"),
            Map.entry("т", "ᴛ"),
            Map.entry("у", "у"),
            Map.entry("ф", "ф"),
            Map.entry("х", "ⅹ"),
            Map.entry("ц", "ц"),
            Map.entry("ч", "ч"),
            Map.entry("ш", "ш"),
            Map.entry("щ", "щ"),
            Map.entry("ъ", "ъ"),
            Map.entry("ы", "ы"),
            Map.entry("ь", "ь"),
            Map.entry("э", "э"),
            Map.entry("ю", "ю"),
            Map.entry("я", "ᴙ")
    );

    private final String[] bb = new String[]{
            "See you later, ",
            "Catch ya later, ",
            "See you next time, ",
            "Farewell, ",
            "Bye, ",
            "Good bye, ",
            "Later, "
    };

    private final String[] qq = new String[]{
            "Good to see you, ",
            "Greetings, ",
            "Hello, ",
            "Howdy, ",
            "Hey, ",
            "Good evening, ",
            "Welcome to SERVERIP1D5A9E, "
    };

    public ChatUtils() {
        super("ChatUtils", Category.MISC);
    }

    public enum CopyButton {
        Off, On
    }
    
    public enum CopySymbol {
        Heart, Sun, Moon
    }
    
    public enum CopyColor {
        Red(0xFF0000),
        Orange(Formatting.GOLD.getColorValue()),
        Yellow(Formatting.YELLOW.getColorValue()),
        Lime(Formatting.GREEN.getColorValue()),
        Aqua(Formatting.DARK_AQUA.getColorValue()),
        Pink(Formatting.LIGHT_PURPLE.getColorValue()),
        DarkPurple(Formatting.DARK_PURPLE.getColorValue()),
        White(Formatting.WHITE.getColorValue()),
        Gray(Formatting.GRAY.getColorValue()),
        Black(Formatting.BLACK.getColorValue());
        
        private final int color;
        CopyColor(int color) {
            this.color = color;
        }
        public int getColor() {
            return color;
        }
    }
    
    public enum TimeColor {
        Gray(Formatting.GRAY),
        Red(Formatting.RED),
        Orange(Formatting.GOLD),
        Yellow(Formatting.YELLOW),
        Lime(Formatting.GREEN),
        Aqua(Formatting.DARK_AQUA),
        Pink(Formatting.LIGHT_PURPLE),
        DarkPurple(Formatting.DARK_PURPLE),
        White(Formatting.WHITE),
        Black(Formatting.BLACK);
        
        private final Formatting formatting;
        TimeColor(Formatting formatting) {
            this.formatting = formatting;
        }
        public Formatting getFormatting() {
            return formatting;
        }
    }
    
    public enum BracketColor {
        Gray(Formatting.GRAY),
        Red(Formatting.RED),
        Orange(Formatting.GOLD),
        Yellow(Formatting.YELLOW),
        Lime(Formatting.GREEN),
        Aqua(Formatting.DARK_AQUA),
        Pink(Formatting.LIGHT_PURPLE),
        DarkPurple(Formatting.DARK_PURPLE),
        White(Formatting.WHITE),
        Black(Formatting.BLACK);
        
        private final Formatting formatting;
        BracketColor(Formatting formatting) {
            this.formatting = formatting;
        }
        public Formatting getFormatting() {
            return formatting;
        }
    }

    @Override
    public void onDisable() {
        nameMap.clear();
    }

    @Override
    public void onUpdate() {
        if (timer.passedMs(15000)) {
            for (PlayerListEntry b : mc.player.networkHandler.getPlayerList()) {
                if (!nameMap.containsKey(b.getProfile().getId())) {
                    nameMap.put(b.getProfile().getId(), b.getProfile().getName());
                }
            }
            timer.reset();
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (welcomer.getValue() != Welcomer.Off && antiSpam.passedMs(3000)) {
            if (event.getPacket() instanceof PlayerListS2CPacket pck) {
                int n2 = (int) Math.floor(Math.random() * qq.length);
                String string1;
                if (mc.player.networkHandler.getServerInfo() != null) {
                    string1 = qq[n2].replace("SERVERIP1D5A9E", mc.player.networkHandler.getServerInfo().address);
                } else string1 = "server";
                if (pck.getActions().contains(PlayerListS2CPacket.Action.ADD_PLAYER)) {
                    for (PlayerListS2CPacket.Entry ple : pck.getPlayerAdditionEntries()) {
                        if (antiBot(ple.profile().getName())) return;
                        if (Objects.equals(ple.profile().getName(), mc.player.getName().getString())) return;
                        if (welcomer.getValue() == Welcomer.Server) {
                            mc.player.networkHandler.sendChatMessage(getPrefix() + string1 + ple.profile().getName());
                            antiSpam.reset();
                        } else sendMessage(string1 + ple.profile().getName());
                        nameMap.put(ple.profile().getId(), ple.profile().getName());
                    }
                }
            }

            if (event.getPacket() instanceof PlayerRemoveS2CPacket pac) {
                for (UUID uuid2 : pac.profileIds) {
                    if (!nameMap.containsKey(uuid2)) return;
                    if (antiBot(nameMap.get(uuid2))) return;
                    if (Objects.equals(nameMap.get(uuid2), mc.player.getName().getString())) return;
                    int n = (int) Math.floor(Math.random() * bb.length);
                    if (welcomer.getValue() == Welcomer.Server) {
                        mc.player.networkHandler.sendChatMessage(getPrefix() + bb[n] + nameMap.get(uuid2));
                        antiSpam.reset();
                    } else sendMessage(bb[n] + nameMap.get(uuid2));
                    nameMap.remove(uuid2);
                }
            }
        }
        if (event.getPacket() instanceof GameMessageS2CPacket pac) {
            IGameMessageS2CPacket pac2 = event.getPacket();
            Text messageContent = pac.content();
            
            if (time.getValue()) {
                String bracketColorCode = bracketColor.getValue().getFormatting().toString();
                String timeColorCode = timeColor.getValue().getFormatting().toString();
                Text timeText = Text.literal(bracketColorCode + "[" + timeColorCode + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + bracketColorCode + "] ");
                messageContent = timeText.copy().append(messageContent);
            }
            
            if (copyButton.getValue() == CopyButton.On && !isSystemMessage(messageContent.getString())) {
                String buttonSymbol = switch (copySymbol.getValue()) {
                    case Heart -> "❤";
                    case Sun -> "☀";
                    case Moon -> "🌙";
                };
                
                String plainText = messageContent.getString().replaceAll("§[0-9a-fk-or]", "");
                int color = copyColor.getValue().getColor();
                
                Text copyButtonText = Text.literal(" " + buttonSymbol + " ")
                    .setStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(color))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, plainText))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                            Text.literal("§kmmm§r").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)))
                        ))
                    );
                messageContent = Text.empty().append(messageContent).append(copyButtonText);
            }
            
            pac2.setContent(messageContent);

            if (mention.getValue()) {
                if (pac.content().getString().contains(mc.player.getName().getString()) && messageTimer.passedMs(1000)) {
                    Managers.NOTIFICATION.publicity("ChatUtils", isRu() ? "Тебя помянули в чате!" : "You were mentioned in the chat!", 4, Notification.Type.WARNING);
                    mc.world.playSound(mc.player, mc.player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 5f, 1f);
                }
            }

            String content = pac.content().getString().toLowerCase();
            if (!pmSound.is(PMSound.Off) && (content.contains("whisper") || content.contains("-> я") || content.contains("-> " + NameProtect.getCustomName()) || content.contains("-> me") || content.contains(" says:"))) {
                Managers.SOUND.playPmSound(pmSound.getValue());
            }
        }
    }

    private boolean isSystemMessage(String message) {
        if (message.isEmpty()) return true;
        if (message.contains("joined the game") || message.contains("left the game")) return true;
        if (message.contains("was slain") || message.contains("achievement")) return true;
        if (message.startsWith("§") && message.length() < 10) return true;
        return false;
    }

    private @NotNull String getPrefix() {
        return switch (prefix.getValue()) {
            case Green -> ">";
            case Global -> "!";
            case None -> "";
        };
    }

    public boolean antiBot(@NotNull String s) {
        if (s.contains("soon_") || s.contains("_npc") || s.contains("CIT-")) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            if (Character.UnicodeBlock.of(s.charAt(i)).equals(Character.UnicodeBlock.CYRILLIC)) {
                return true;
            }
        }
        return false;
    }
    
    private String applyAntiBwFilter(String message) {
        String result = message;
        result = result.replace("х", "x");
        result = result.replace("Х", "X");
        result = result.replace("у", "y");
        result = result.replace("У", "Y");
        result = result.replace("е", "e");
        result = result.replace("Е", "E");
        result = result.replace("а", "a");
        result = result.replace("А", "A");
        result = result.replace("о", "o");
        result = result.replace("О", "O");
        result = result.replace("пизд", "пuзд");
        result = result.replace("Пизд", "Пuзд");
        result = result.replace("ПИЗД", "ПuЗД");
        result = result.replace("ПUЗД", "ПuЗД");
        result = result.replace("че бате", "чe бaтe");
        result = result.replace("ЧЕ БАТЕ", "ЧE БAТE");
        result = result.replace("Че Бате", "Чe Бaтe");
        return result;
    }
    
    private String applyCustomFont(String message) {
        String result = message.toLowerCase();
        for (Map.Entry<String, String> entry : customFontMap.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    @EventHandler
    public void onPacketSend(PacketEvent.@NotNull Send e) {
        if (e.getPacket() instanceof ChatMessageC2SPacket pac) {
            if (antiCoordLeak.getValue() && pac.chatMessage().replaceAll("\\D", "").length() >= 6) {
                sendMessage("[ChatUtils] " + (isRu() ? "В сообщении содержатся координаты!" : "The message contains coordinates!"));
                e.cancel();
            }

            messageTimer.reset();
        }

        if (fullNullCheck()) return;
        if (e.getPacket() instanceof ChatMessageC2SPacket pac && (zov.getValue() || wavy.getValue() || translit.getValue() || antiBwFilter.getValue() || customFont.getValue())) {

            if (Objects.equals(pac.chatMessage(), skip)) {
                return;
            }

            if (mc.player.getMainHandStack().getItem() == Items.FILLED_MAP || mc.player.getOffHandStack().getItem() == Items.FILLED_MAP)
                return;

            if (pac.chatMessage().startsWith("/") || pac.chatMessage().startsWith(Managers.COMMAND.getPrefix()))
                return;

            String message = pac.chatMessage();
            
            if (customFont.getValue()) {
                message = applyCustomFont(message);
            } else {
                if (antiBwFilter.getValue()) {
                    message = applyAntiBwFilter(message);
                }
                if (zov.getValue()) {
                    StringBuilder builder = new StringBuilder();
                    for (char Z : message.toCharArray()) {
                        if ('З' == Z || 'з' == Z) {
                            builder.append("Z");
                        } else if ('В' == Z || 'в' == Z) {
                            builder.append("V");
                        } else {
                            builder.append(Z);
                        }
                    }
                    message = builder.toString();
                }
                if (wavy.getValue()) {
                    StringBuilder builder = new StringBuilder();
                    boolean up = false;
                    for (char C : message.toCharArray()) {
                        if (up) {
                            builder.append(Character.toUpperCase(C));
                        } else {
                            builder.append(Character.toLowerCase(C));
                        }
                        up = Character.isLetter(C) != up;
                    }
                    message = builder.toString();
                }
                if (translit.getValue())
                    message = transliterate(message);
            }
            skip = message;
            mc.player.networkHandler.sendChatMessage(skip);
            e.cancel();
        }
    }

    public String transliterate(String text) {
        StringBuilder result = new StringBuilder();

        for (char ch : text.toCharArray()) {
            String str = ruToEng.get(ch + "");
            result.append(str != null ? str : ch);
        }

        return result.toString();
    }

    private enum Welcomer {
        Off, Server, Client
    }

    private enum Prefix {
        Green, Global, None
    }

    public enum PMSound {
        Off, Default, Custom
    }
}
