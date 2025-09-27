package xbwk.addon.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import xbwk.addon.Elytraskyaddon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

public class Botmode extends Module {
    public enum CommandType {
        W("/w"),
        TELL("/tell"),
        MSG("/msg"),
        WHISPER("/whisper");

        private final String command;

        CommandType(String command) {
            this.command = command;
        }

        @Override
        public String toString() {
            return command;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTiming = settings.createGroup("Timing Settings");
    private final SettingGroup sgLogSettings = settings.createGroup("Log Settings");

    // Master player setting
    private final Setting<String> masterPlayer = sgGeneral.add(new StringSetting.Builder()
            .name("master-player")
            .description("Player who can control this bot")
            .defaultValue("")
            .build()
    );

    // Command type setting
    private final Setting<CommandType> commandType = sgGeneral.add(new EnumSetting.Builder<CommandType>()
            .name("command-type")
            .description("Type of private message command to use")
            .defaultValue(CommandType.MSG)
            .build()
    );

    // Log directory setting
    private final Setting<String> logDirectory = sgLogSettings.add(new StringSetting.Builder()
            .name("log-directory")
            .description("Directory where base logs are stored")
            .defaultValue("base_finder_logs")
            .build()
    );

    // Timing settings
    private final Setting<Integer> baseInfoDelay = sgTiming.add(new IntSetting.Builder()
            .name("base-info-delay")
            .description("Delay between sending base info messages (seconds)")
            .defaultValue(3)
            .min(1)
            .sliderRange(1, 10)
            .build()
    );

    private final Setting<Integer> statusUpdateDelay = sgTiming.add(new IntSetting.Builder()
            .name("status-update-delay")
            .description("Delay between status updates (seconds)")
            .defaultValue(2)
            .min(1)
            .sliderRange(1, 10)
            .build()
    );

    // Runtime variables
    private final List<String> baseLogEntries = new ArrayList<>();
    private int statusTimer = 0;
    private int baseInfoIndex = 0;
    private int baseInfoTimer = 0;
    private long startTime;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyy-MM-dd");

    // 添加对Advertise模块的引用
    private xbwk.addon.modules.Advertise advertiseModule;

    public Botmode() {
        super(Elytraskyaddon.CATEGORY, "bot-mode", "Follows commands from specified player");
    }

    @Override
    public void onActivate() {
        startTime = System.currentTimeMillis();
        statusTimer = 0;
        baseInfoIndex = 0;
        baseInfoTimer = 0;
        baseLogEntries.clear();

        // 获取Advertise模块引用
        advertiseModule = Modules.get().get(xbwk.addon.modules.Advertise.class);

        info("Botmode enabled. The owner: " + masterPlayer.get());
    }

    @EventHandler
    private void onMessageReceive(SendMessageEvent event) {
        if (mc.player == null || masterPlayer.get().isEmpty()) return;

        String message = event.message;
        if (message.startsWith("=")) {
            String command = message.split(" ")[0].toLowerCase();

            switch (command) {
                case "=checkelytra":
                    handleElytraCheck();
                    event.cancel();
                    break;
                case "=base":
                    handleBaseInfo();
                    event.cancel();
                    break;
                case "=status":
                    startStatusUpdates();
                    event.cancel();
                    break;
                case "=readlogs":
                    readBaseLogs();
                    event.cancel();
                    break;
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;

        // Handle status update timer
        if (statusTimer > 0) {
            statusTimer--;
            if (statusTimer == 0) {
                sendStatusUpdate();
                statusTimer = statusUpdateDelay.get() * 20;
            }
        }

        // Handle base info timer
        if (baseInfoTimer > 0) {
            baseInfoTimer--;
            if (baseInfoTimer == 0 && baseInfoIndex < baseLogEntries.size()) {
                sendBaseLogEntry(baseLogEntries.get(baseInfoIndex));
                baseInfoIndex++;
                baseInfoTimer = baseInfoDelay.get() * 20;
            }
        }
    }

    private void handleElytraCheck() {
        ChatUtils.sendPlayerMsg("相关信息已私信");

        int totalElytra = 0;
        int fullDurability = 0;
        int currentDurability = 0;

        // Check inventory for elytra
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.ELYTRA) {
                totalElytra++;
                if (stack.getDamage() == 0) {
                    fullDurability++;
                }
                currentDurability = stack.getMaxDamage() - stack.getDamage();
            }
        }

        // Send private message
        String privateMessage = String.format("鞘翅数量: %d | 满耐久: %d | 当前耐久: %d",
                totalElytra, fullDurability, currentDurability);
        sendPrivateMessage(privateMessage);
    }

    private void handleBaseInfo() {
        readBaseLogs();
        if (baseLogEntries.isEmpty()) {
            ChatUtils.sendPlayerMsg("未发现任何基地日志");
            return;
        }

        ChatUtils.sendPlayerMsg("开始发送基地日志信息 (" + baseLogEntries.size() + " 条记录)");
        baseInfoIndex = 0;
        baseInfoTimer = 1; // Start sending on next tick
    }

    private void readBaseLogs() {
        baseLogEntries.clear();

        try {
            String meteorDir = System.getProperty("user.dir");
            Path logDirPath = Paths.get(meteorDir, logDirectory.get());

            if (!Files.exists(logDirPath)) {
                ChatUtils.sendPlayerMsg("日志目录不存在: " + logDirPath);
                return;
            }

            // Get today's log file
            String dateStr = fileDateFormat.format(new Date());
            String fileName = "bases_" + dateStr + ".csv";
            Path logFile = logDirPath.resolve(fileName);

            if (!Files.exists(logFile)) {
                ChatUtils.sendPlayerMsg("今日日志文件不存在: " + fileName);
                return;
            }

            // Read all lines from the log file
            List<String> lines = Files.readAllLines(logFile);

            // Skip header line and process each entry
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!line.trim().isEmpty()) {
                    baseLogEntries.add(line);
                }
            }

            ChatUtils.sendPlayerMsg("成功读取 " + baseLogEntries.size() + " 条基地日志记录");

        } catch (IOException e) {
            ChatUtils.sendPlayerMsg("读取日志文件时出错: " + e.getMessage());
            error("Failed to read base logs: " + e.getMessage());
        }
    }

    private void startStatusUpdates() {
        statusTimer = 1; // Start sending on next tick
    }

    private void sendStatusUpdate() {
        // 检查Advertise模块状态
        boolean isAdvertising = advertiseModule != null && advertiseModule.isActive();

        String status = String.format("在线时长: %s | 生命值: %.1f | 状态: %s | 广告: %s | 日志记录: %d",
                getUptime(),
                mc.player.getHealth(),
                getCurrentStatus(),
                isAdvertising ? "开启" : "关闭",
                baseLogEntries.size());

        ChatUtils.sendPlayerMsg(status);
    }

    private void sendBaseLogEntry(String logEntry) {
        // Parse CSV format: Time,Base ID,X,Y,Z,Block Count,Volume,Density,Block Types
        String[] parts = logEntry.split(",");
        if (parts.length >= 8) {
            try {
                String time = parts[0];
                String baseId = parts[1];
                int x = Integer.parseInt(parts[2]);
                int y = Integer.parseInt(parts[3]);
                int z = Integer.parseInt(parts[4]);
                int blockCount = Integer.parseInt(parts[5]);
                double volume = Double.parseDouble(parts[6]);
                double density = Double.parseDouble(parts[7]);

                String info = String.format("基地 #%d/%d | 时间: %s | 位置: %d %d %d | 方块: %d | 密度: %.4f",
                        baseInfoIndex + 1,
                        baseLogEntries.size(),
                        time,
                        x, y, z,
                        blockCount,
                        density);

                sendPrivateMessage(info);

            } catch (NumberFormatException e) {
                sendPrivateMessage("日志格式错误: " + logEntry);
            }
        } else {
            sendPrivateMessage("无效日志条目: " + logEntry);
        }
    }

    private void sendPrivateMessage(String message) {
        String command = String.format("%s %s %s",
                commandType.get().toString(),
                masterPlayer.get(),
                message);

        ChatUtils.sendPlayerMsg(command);
    }

    private String getUptime() {
        long uptime = System.currentTimeMillis() - startTime;
        long hours = uptime / (1000 * 60 * 60);
        long minutes = (uptime % (1000 * 60 * 60)) / (1000 * 60);
        return String.format("%d小时 %d分钟", hours, minutes);
    }

    private String getCurrentStatus() {
        if (statusTimer > 0) {
            return "状态更新";
        } else if (baseInfoTimer > 0) {
            return "扫图";
        } else {
            return "静止";
        }
    }

    private String getCurrentTime() {
        return timeFormat.format(new Date());
    }
}