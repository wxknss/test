package thunder.hack.features.modules.misc;

import thunder.hack.core.Managers;
import thunder.hack.features.hud.impl.StaffBoard;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static thunder.hack.features.modules.client.ClientSettings.isRu;

public class Spammer extends Module {
    public static ArrayList<String> SpamList = new ArrayList<>();
    public Setting<SpamMode> spamMode = new Setting<>("SpamMode", SpamMode.Chat);
    public Setting<WhisperPrefix> whisperPrefix = new Setting<>("WhisperPrefix", WhisperPrefix.W, v -> spamMode.getValue() == SpamMode.Whispers);
    public Setting<Boolean> global = new Setting<>("Global", true, v -> spamMode.getValue() == SpamMode.Chat);
    public Setting<Boolean> antiSpam = new Setting<>("AntiSpam", false);
    public Setting<Float> delay = new Setting<>("Delay", 5f, 0f, 30f);
    
    private final Timer timer_delay = new Timer();
    private final Random random = new Random();

    public Spammer() {
        super("Spammer", Category.MISC);
    }

    public static void loadSpammer() {
        try {
            File file = new File("ThunderHackRecode/misc/spammer.txt");

            if (!file.exists()) file.createNewFile();
            new Thread(() -> {
                try {
                    FileInputStream fis = new FileInputStream(file);
                    InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                    BufferedReader reader = new BufferedReader(isr);
                    ArrayList<String> lines = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }

                    boolean newline = false;

                    for (String l : lines) {
                        if (l.equals("")) {
                            newline = true;
                            break;
                        }
                    }

                    SpamList.clear();
                    ArrayList<String> spamList = new ArrayList<>();

                    if (newline) {
                        StringBuilder spamChunk = new StringBuilder();

                        for (String l : lines) {
                            if (l.equals("")) {
                                if (!spamChunk.isEmpty()) {
                                    spamList.add(spamChunk.toString());
                                    spamChunk = new StringBuilder();
                                }
                            } else {
                                spamChunk.append(l).append(" ");
                            }
                        }
                        spamList.add(spamChunk.toString());
                    } else spamList.addAll(lines);
                    SpamList = spamList;
                } catch (Exception ignored) {
                }
            }).start();
        } catch (IOException ignored) {
        }
    }

    public String getPlayerName() {
        try {
            List<String> list = StaffBoard.getOnlinePlayer();
            if (list.isEmpty())
                return "";
            return list.get(random.nextInt(0, list.size() - 1));
        } catch (NullPointerException e) {
            return null;
        }
    }

    public static String generateRandomSymbol() {
        Random random = new Random();
        String randomSymbol = "[";
        randomSymbol += (char) (random.nextInt(26) + 'a');
        randomSymbol += random.nextInt(10);
        randomSymbol += (char) (random.nextInt(26) + 'a');
        randomSymbol += "]";
        return randomSymbol;
    }

    @Override
    public void onEnable() {
        loadSpammer();
    }

    @Override
    public void onUpdate() {
        if (timer_delay.passedMs((long) (delay.getValue() * 1000))) {
            if (SpamList.isEmpty()) {
                disable(isRu() ? "Вставь фразочки в minecraft\thunderhackrecode\mist\spammer.txt" : "The spammer file is empty! minecraft\thunderhackrecode\mist\spammer.txt");
                return;
            }
            
            String c = SpamList.get(random.nextInt(SpamList.size()));
            
            if (antiSpam.getValue()) {
                c += generateRandomSymbol();
            }
            
            if (spamMode.getValue() == SpamMode.Chat) {
                if (c.charAt(0) == '/') {
                    c = c.replace("/", "");
                    mc.player.networkHandler.sendCommand(c);
                } else {
                    mc.player.networkHandler.sendChatMessage(global.getValue() ? "!" + c : c);
                }
            } else {
                try {
                    String prefix = whisperPrefix.getValue().prefix;
                    String playerName = getPlayerName();
                    if (playerName != null && !playerName.isEmpty()) {
                        mc.player.networkHandler.sendCommand(prefix + playerName + " " + c);
                    }
                } catch (NullPointerException ignored) {
                }
            }

            timer_delay.reset();
        }
    }

    public enum SpamMode {
        Chat,
        Whispers
    }

    public enum WhisperPrefix {
        M("m "),
        W("w "),
        Msg("msg "),
        Tell("tell ");

        final String prefix;

        WhisperPrefix(String p) {
            prefix = p;
        }
    }
}
