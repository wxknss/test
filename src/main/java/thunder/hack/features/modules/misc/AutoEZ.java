package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import thunder.hack.core.Managers;
import thunder.hack.events.impl.EventDeath;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.combat.Aura;
import thunder.hack.features.modules.combat.AutoCrystal;
import thunder.hack.setting.Setting;
import thunder.hack.utility.ThunderUtility;
import thunder.hack.utility.Timer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;

import static thunder.hack.features.modules.client.ClientSettings.isRu;

public final class AutoEZ extends Module {
    public static ArrayList<String> EZWORDS = new ArrayList<>();
    public Setting<Boolean> global = new Setting<>("global", true);
    public Setting<Float> delay = new Setting<>("Delay", 5.1f, 1.0f, 10.0f);

    String[] EZ = new String[]{
            "EZZZZZZZZZ проглотив хуй скажешь что? или молча будешь это делать",
            "ПАПУСЯНКА СЛАБАЯ %player% ПАПУСЯНКА ИГРАТЬ НЕ УМЕЕШЬ НУБЛАНИНА НУБАСОСИНА НУБША НУБЛАНИЩЕ НУБСТВО ТЫ ПОЛНОЕ",
            "ТЫ НАСТОЛЬКО БОМЖИК %player% ЧТО ДАЖЕ КАПС ЛОКА У ТЕБЯ НЕТ НА КЛАВИАТУРЕ",
            "%player% мм тут ты так красиво пал правда как лооох лашня ужас просто кошмар какой то",
            "%player% посмотри назад ХАХА на тебя голуби насряяялиииии",
            "%player% ЕЗЕЗЕЗЕЕЗЕЗЕЗЕЗЕЗЕЕЗЕЗЗ ЕЗЕЗЕЗЗЕЗЕЗЗЗЗ БЛЯЯЯ ХАВХАВХААЫХЫЗХ EZZZZZZZZZZZZZZZZZZ",
            "%player% ты легкииииий у тебя даже залупосракояльница в носу вылезай из люка или тебе вкусно в канализации воду пить",
            "%player% восьмитазовоканальный ты широкодырожопный человечик в грязой одежде и спящий в мусорном пакете",
            "%player% пиздец ты слился словно димонопроебонизмокислотноушепьющепотолочноблядовидносвиноеботальнобезмзоглохуесанскошлюхорототель",
            "ТЫЫЫЫЫЫЫ %player% НЮХААЕШШЬЬ ЦВЕТЫЫЫ %player% ромашка не моя жопа нюхает твояяяяяяя %player%",
            "%player% ТЕБЕ ТОЛЬКО С БУДИЛЬНИКОМ СВОИМ ПВПХАТЬСЯ ХАВХАВХ",
            "%player% тебе не стыдно пить зеленку и запивать ее из одуванчиков и лапухов?",
            "%player% Я СТАНУ ДЛЯ ТЕБЯ ЗУБНЫМ ВРАЧЕМ ХАВХЫВХИХЫХАЗХИХВ",
            "%player% У ТЕБЯ БАТАРЕЙКИ КОНЧИЛИСЬ ОТ МОЗГОВ И ТЫ НАСТОЛЬКО ЛЕГКО ТАК СДОХ",
            "%player% ТРАХАЙСЯ С АВТОБУСАМИ НЕМОЩНЫЙ СЕКС У ТЕБЯ ВСЮ ЖИЗНЬ С АВТОБУСАМИ БЛЯТЬ ХВАХЫФВЗАЫВЗВЫЗХАВЗХВАХ",
            "%player% ЛЕЖАТЬ НА СПАВНЕ ГУСЕНИЦА ВУПСЕНЬ И ПУПСЕНЬ БЕГИ К ЛУНТИКУ В ДУПЛО ПОД СТОЛ",
            "%player% ПОЧЕМУ ТЫ ТАКОЙ ЛЕГКИЙ ЧТО ПРЯМО УМОЛЯЕШЬ ОТДАТЬ СВОЙ ДНЕВНИЙ ШКОЛЬНЫЙ?",
            "%player% КУШАЙ СЕБЯ БОБРОСУСЛИК АСТАСВИНОПАЛОЧНОБИХИМИДОПИРИДОВЫЙ БАШМАКОБУТЫЛОЧНОЖОПОСАДЯЩИЙСЯ ОТХУЕСАРАЕСАСЫВАТЕЛЬ",
            "%player% ТЫ 0 ЗХАВВЗХВПАЗХАЗВХЗХПАВАЗХ %player% 00000000000000000000000000 %player% биомусорничный лох",
            "ты летающая книга по литературе тебя даже по телевизору показали %player% в рекламе телевикторины",
            "автомобильное радио сообщает вам %player% очень важную информацию для вас чтобы вы слились в майнкрафте",
            "%player% кошмар ты так слился легко что даже от страха не возраждаешься и читаешь это шас же ответишь",
            "%player% плохую ты музыку слушаешь волосы плохо лежат свет в комнате твой вообще плохой",
            "%player% ежели вы являяетесь диким животным то напишите пожалуйста или приказываю в чат вашу злость вы же раб и послушаетесь уже пишешь верно ужас",
            "ХУЙ САСНИ И ДАВОЛЬНИЙ ХАДЫЫЫ %player% А КОГДА ТЫ БЫЛ МАЛАДЫМ ТИ НИ ЛЮБИЛ И НЕ ДРАЧЫЛ",
            "%player% часть сыра много много много сырааа прям кошмар дырок вкусный ты был вчера теперь тебя больше нет БОЛЬШЕЕЕ НЕТ БОЛЬШЕ НЕЕЕЕЕЕЕЕТТТ БЛЯЯЯЯЯ",
            "ДУШИТЬ ТЕБЯ В ВАННОЙ %player% И СНИМАААТЬ СВАААЮ УСТАЛАСТЬ %player% ТРАХАЕТСЯ В РОТ С %player% ХАХАХАХАХ БУДЬ ФАНАТОМ КРИСТИНКИ ТЫ НИЩИЙ НОГАМИ ИГРАЕШЬ СЛАБОСТЬ ТУПАЯ",
            "%player% ЧИТ НАСТРОЙ БЛЯ ИДИ ВЫЙГРАЙ ПВП ПРОТИВ НПС ВЕЩИ ДЬЯВОЛА И С ДЕДОМ МОРОЗОМ ЕЩЕ ПОПРОБУЙ ВДРУГ НЕ СОЛЬЕШЬСЯ ЕМУ ПЕРЕСИДИ ЕГО ДОЖДИСЬ КОГДА УЙДУТ ЕСЛИ НЕ ЛОХ ХД",
            "%player% ТЫ НАСТОЛЬКО БЛЯТЬ СЛАБАЯ ХУЕПРИЕМНИЦА ТЕРПИЛЬНИЦА ЧТО ДАЖЕ ОДНО ХП НЕ СНЕСЛА ХЗЫВХЗЫХВ ПИЗДЕЦ ТЫ СЛАБОСТЬ ЕБАННАЯ",
            "%player% ДОПАРКИНСОНИЛСЯ ДО ПОЛЕТА В КОСМАС И НА СПАВН НЕ ВЗЛЕТЕВ НА ТОПЛИВЕ ИЗ СВОЕЙ ЖОПЫ ВО ВРЕМЯ БОМБЕЖА"
    };

    private final Setting<ModeEn> mode = new Setting<>("Mode", ModeEn.Basic);
    private final Setting<ServerMode> server = new Setting<>("Server", ServerMode.Universal);

    private final Timer ezTimer = new Timer();

    public AutoEZ() {
        super("AutoEZ", Category.MISC);
        loadEZ();
    }

    public static void loadEZ() {
        try {
            File file = new File("ThunderHackRecode/misc/AutoEZ.txt");
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
                        if (l.isEmpty()) {
                            newline = true;
                            break;
                        }
                    }
                    EZWORDS.clear();
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

                    EZWORDS = spamList;
                } catch (Exception ignored) {
                }
            }).start();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void onEnable() {
        loadEZ();
        ezTimer.reset();
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (fullNullCheck()) return;
        
        if (!ezTimer.passedMs((long) (delay.getValue() * 1000))) return;
        
        if (e.getPacket() instanceof GameMessageS2CPacket) {
            final GameMessageS2CPacket packet = e.getPacket();
            String message = packet.content().getString();
            String killedName = null;
            
            if (server.getValue() == ServerMode.Universal) {
                return;
            }
            
            if (server.getValue() == ServerMode.FunnyGame) {
                if (message.contains("Вы убили игрока")) {
                    killedName = ThunderUtility.solveName(message);
                    if (Objects.equals(killedName, "FATAL ERROR")) return;
                    if (killedName.equalsIgnoreCase(mc.player.getName().getString())) return;
                    if (Managers.FRIEND.isFriend(killedName)) return;
                    sayEZ(killedName);
                    ezTimer.reset();
                }
                return;
            }
            
            if (server.getValue() == ServerMode.BedWars) {
                String myName = mc.player.getName().getString();
                
                if (message.contains("BedWars") && message.contains("убил") && message.contains(myName)) {
                    try {
                        String afterPrefix = message.substring(message.indexOf("»") + 2);
                        String[] parts = afterPrefix.split("убил");
                        if (parts.length >= 2) {
                            killedName = parts[1].trim().replace("!", "");
                        }
                    } catch (Exception ignored) {}
                }
                
                if (killedName == null && message.contains("BedWars") && message.contains("выпал из мира") && message.contains("боролся с") && message.contains(myName)) {
                    try {
                        String afterPrefix = message.substring(message.indexOf("»") + 2);
                        int startIndex = afterPrefix.indexOf(" выпал из мира");
                        if (startIndex > 0) {
                            killedName = afterPrefix.substring(0, startIndex).trim();
                        }
                    } catch (Exception ignored) {}
                }
                
                if (killedName != null && !killedName.isEmpty()) {

                    if (killedName.equalsIgnoreCase(myName)) return;
                    if (Managers.FRIEND.isFriend(killedName)) return;
                    sayEZ(killedName);
                    ezTimer.reset();
                }
                return;
            }
            
            if (server.getValue() == ServerMode.MineGame) {
                String myName = mc.player.getName().getString();
                
                if (message.contains("⚔") && message.contains("был убит игроком") && message.contains(myName)) {
                    try {
                        int startIndex = message.indexOf("⚔ Игрок ") + 9;
                        int endIndex = message.indexOf(" был убит игроком");
                        if (startIndex > 8 && endIndex > startIndex) {
                            killedName = message.substring(startIndex, endIndex);
                        }
                    } catch (Exception ignored) {}
                    
                    if (killedName != null && !killedName.isEmpty()) {
                        if (killedName.equalsIgnoreCase(myName)) return;
                        if (Managers.FRIEND.isFriend(killedName)) return;
                        sayEZ(killedName);
                        ezTimer.reset();
                    }
                }
                return;
            }
        }
    }

    @EventHandler
    public void onDeath(EventDeath e) {
        if (server.getValue() != ServerMode.Universal) return;
        if (!ezTimer.passedMs((long) (delay.getValue() * 1000))) return;
        
        String killedName = e.getPlayer().getName().getString();
        if (killedName.equalsIgnoreCase(mc.player.getName().getString())) return;
        if (Managers.FRIEND.isFriend(killedName)) return;
        
        if (Aura.target != null && Aura.target == e.getPlayer()) {
            sayEZ(killedName);
            ezTimer.reset();
            return;
        }
        if (AutoCrystal.target != null && AutoCrystal.target == e.getPlayer()) {
            sayEZ(killedName);
            ezTimer.reset();
        }
    }

    public void sayEZ(String pn) {
        String finalword;
        if (mode.getValue() == ModeEn.Basic) {
            int n = (int) Math.floor(Math.random() * EZ.length);
            finalword = EZ[n].replace("%player%", pn);
        } else {
            if (EZWORDS.isEmpty()) {
                sendMessage(isRu() ? "Файл с AutoEZ пустой!" : "AutoEZ.txt is empty!");
                return;
            }
            finalword = EZWORDS.get(new Random().nextInt(EZWORDS.size()));
            finalword = finalword.replaceAll("%player%", pn);
        }
        mc.player.networkHandler.sendChatMessage(global.getValue() ? "!" + finalword : finalword);
    }

    public enum ModeEn {
        Custom,
        Basic
    }

    public enum ServerMode {
        Universal,
        FunnyGame,
        BedWars,
        MineGame
    }
}
