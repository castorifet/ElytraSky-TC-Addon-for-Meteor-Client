package xbwk.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Yuzusayings extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Delay (ticks)")
            .defaultValue(600)
            .min(20)
            .max(3600)
            .sliderMin(100)
            .sliderMax(1200)
            .build()
    );

    private final Setting<Boolean> showPrefix = sgGeneral.add(new BoolSetting.Builder()
            .name("show-prefix")
            .description("Display prefix")
            .defaultValue(true)
            .build()
    );

    private List<String> messages = new ArrayList<>();
    private int timer = 0;
    private Random random = new Random();

    public Yuzusayings() {
        super(xbwk.addon.Elytraskyaddon.CATEGORY, "yuzu-sayings", "Read the list file and send random messages");
    }

    @Override
    public void onActivate() {
        loadMessages();
        timer = 0;
        if (mc.player != null) {
            info("Loaded " + messages.size() + " messages");
        }
    }

    @Override
    public void onDeactivate() {
        messages.clear();
        if (mc.player != null) {
            info("Stopped");
        }
    }

    private void loadMessages() {
        messages.clear();
        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("yuzu_sayings.txt");
            if (inputStream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.trim().isEmpty() && !line.trim().startsWith("#")) {
                            messages.add(line.trim());
                        }
                    }
                }
                if (mc.player != null) {
                    info("Loaded " + messages.size() + " messages");
                }
            } else {
                if (mc.player != null) {
                    error("yuzu_sayings.txt not found");
                }
                addDefaultMessages();
            }
        } catch (Exception e) {
            if (mc.player != null) {
                error("Failed to read: " + e.getMessage());
            }
            addDefaultMessages();
        }
    }

    private void addDefaultMessages() {
        messages.add("此情无计可消除，才Cia眉llo～(∠・ω< )⌒★，Cia上心llo～(∠・ω< )⌒★。《一剪梅·红藕香残玉簟秋》");
        if (mc.player != null) {
            info("using default messages " + messages.size() + " messages");
        }
    }

    // 使用正确的 TickEvent
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || messages.isEmpty()) return;

        timer++;
        if (timer >= delay.get()) {
            timer = 0;
            sendRandomMessage();
        }
    }

    private void sendRandomMessage() {
        if (messages.isEmpty() || mc.player == null) return;

        String message = messages.get(random.nextInt(messages.size()));

        if (showPrefix.get()) {
            ChatUtils.sendPlayerMsg("[Yuzu] " + message);
        } else {
            ChatUtils.sendPlayerMsg(message);
        }
    }

    // 重新加载消息的方法
    public void reloadMessages() {
        loadMessages();
        if (mc.player != null) {
            info("Reloaded " + messages.size() + " messages");
        }
    }

    // 获取当前消息数量
    public int getMessageCount() {
        return messages.size();
    }

    // 手动发送随机消息
    public void sendRandomMessageNow() {
        sendRandomMessage();
    }

}
