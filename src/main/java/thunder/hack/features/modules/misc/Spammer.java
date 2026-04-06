package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.PlayerUpdateEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class Spammer extends Module {
    public Spammer() {
        super("Spammer", Category.MISC);
    }

    private final Setting<Integer> delay = new Setting<>("Delay", 5100, 100, 30000);
    private final Setting<Boolean> random = new Setting<>("Random", false);
    private final Setting<Boolean> antiSpam = new Setting<>("AntiSpam", false);

    private final Timer timer = new Timer();
    private final Random randomGen = new Random();
    private List<String> messages = new ArrayList<>();
    private int currentIndex = 0;
    private String lastMessage = "";
    private List<String> playerNames = new ArrayList<>();
    private int playerIndex = 0;

    @Override
    public void onEnable() {
        loadMessages();
        if (messages.isEmpty()) {
            disable();
            return;
        }
        currentIndex = 0;
        playerIndex = 0;
        timer.reset();
    }

    @EventHandler
    public void onUpdate(PlayerUpdateEvent e) {
        if (fullNullCheck()) return;
        if (!timer.passedMs(delay.getValue())) return;
        
        sendMessage();
        timer.reset();
    }
    
    private void sendMessage() {
        if (messages.isEmpty()) return;
        
        String message = getNextMessage();
        
        if (message.contains("%player%")) {
            updatePlayerList();
            String playerName = getNextPlayerName();
            if (playerName != null) {
                message = message.replace("%player%", playerName);
            }
        }
        
        if (antiSpam.getValue() && message.equals(lastMessage)) return;
        
        mc.player.networkHandler.sendChatMessage(message);
        lastMessage = message;
    }
    
    private String getNextMessage() {
        if (random.getValue()) {
            return messages.get(randomGen.nextInt(messages.size()));
        } else {
            String msg = messages.get(currentIndex);
            currentIndex = (currentIndex + 1) % messages.size();
            return msg;
        }
    }
    
    private String getNextPlayerName() {
        if (playerNames.isEmpty()) return null;
        String name = playerNames.get(playerIndex);
        playerIndex = (playerIndex + 1) % playerNames.size();
        return name;
    }
    
    private void updatePlayerList() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        
        playerNames = mc.getNetworkHandler().getPlayerList().stream()
            .map(PlayerListEntry::getProfile)
            .map(profile -> profile.getName())
            .filter(name -> !name.equalsIgnoreCase(mc.player.getName().getString()))
            .filter(name -> !ModuleManager.friendManager.isFriend(name))
            .collect(Collectors.toList());
    }
    
    private void loadMessages() {
        messages.clear();
        Path path = Paths.get("thunderhack/spammer.txt");
        
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.createFile(path);
                return;
            }
            
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        messages.add(line);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
