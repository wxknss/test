package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.NotNull;
import thunder.hack.events.impl.EventAttack;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class EbatteSratte extends Module {
    private final Setting<Integer> delay = new Setting<>("Delay", 5, 1, 30);
    private final Setting<Server> server = new Setting<>("Server", Server.FunnyGame);
    private final Setting<Messages> mode = new Setting<>("Mode", Messages.Default);

    private static final String[] WORDS = new String[]{
            "скажи себе в рот с чужого пениса провокации и оскорбления адресованные тебе же из за твоего самоотсоса твоего хуя на лбу ебать ты конечно уродище мудогноевыживающее хуемозглонедотсоснообразное",
            "что скажешь в пизду во время отлиза своим свинохуярищем не пытаясь это отрицать дурогандонище отсосное почему ты настолько нищий что на таком донате играешь отвечай слабая шлюхохуеглотина",
            "все слова это хуй скажи что то ниже своим ебалом в хуй жалкий трипиздазвонохуеавтопроблядник ебалорыгализный еблесвоеочкожадный пока тебя ебали что написали на твоем теле",
            "пищи реще слабость ты ебанная схуяли ты такое медленное говнище РЕЩЕ БЛЯТЬ ХАХАХ ебать ты инвалидище свинозалупное чекай я твое мохномудачище нахуй бросаю в говно которое ты ел вместо нищета",
            "далбаебище ебанное ты хотя бы чекни свой скин чтобы осмелиться с хуем в жопе чат открывать отсосница ты потоскливая хзахзазхаваазхвзхазхв твой рот уже ваншотнут и ты не дал отпора даже",
    };

    private final Timer timer = new Timer();
    private ArrayList<String> words = new ArrayList<>();

    public EbatteSratte() {
        super("EbatteSratte", Module.Category.MISC);
        loadEZ();
    }

    @Override
    public void onEnable() {
        loadEZ();
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onAttackEntity(@NotNull EventAttack event) {
        if (event.getEntity() instanceof PlayerEntity && !event.isPre()) {
            if (timer.passedS(delay.getValue())) {
                PlayerEntity entity = (PlayerEntity) event.getEntity();
                if (entity == null) return;

                int n;

                if (mode.getValue() == Messages.Default) n = (int) Math.floor(Math.random() * WORDS.length);
                else n = (int) Math.floor(Math.random() * words.size());

                String chatPrefix = switch (server.getValue()) {
                    case FunnyGame -> "!";
                    case OldServer -> ">";
                    case DirectMessage -> "/msg ";
                    case Local -> "";
                };

                if (chatPrefix.contains("/"))
                    mc.getNetworkHandler().sendChatCommand("/msg " + entity.getName().getString() + " " + (mode.getValue() == Messages.Default ? WORDS[n] : words.get(n)));
                else
                    mc.getNetworkHandler().sendChatMessage(chatPrefix + entity.getName().getString() + " " + (mode.getValue() == Messages.Default ? WORDS[n] : words.get(n)));


                timer.reset();
            }
        }
    }

    public void loadEZ() {
        try {
            File file = new File("ThunderHackRecode/misc/EbatteSratte.txt");
            if (!file.exists() && !file.createNewFile())
                sendMessage("Error with creating file");

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
                        if (l.isEmpty()) {
                            newline = true;
                            break;
                        }
                    }
                    words.clear();
                    ArrayList<String> spamList = new ArrayList<>();
                    if (newline) {
                        StringBuilder spamChunk = new StringBuilder();
                        for (String l : lines) {
                            if (l.isEmpty()) {
                                if (!spamChunk.isEmpty()) {
                                    spamList.add(spamChunk.toString());
                                    spamChunk = new StringBuilder();
                                }
                            } else spamChunk.append(l).append(" ");
                        }
                        spamList.add(spamChunk.toString());
                    } else spamList.addAll(lines);

                    words = spamList;
                } catch (Exception ignored) {
                }
            }).start();
        } catch (IOException ignored) {
        }
    }

    public enum Server {
        FunnyGame,
        DirectMessage,
        OldServer,
        Local
    }

    public enum Messages {
        Default,
        Custom
    }
}
