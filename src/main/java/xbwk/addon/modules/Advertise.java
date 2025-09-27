package xbwk.addon.modules;

import meteordevelopment.meteorclient.systems.modules.Module;

import meteordevelopment.meteorclient.settings.*;

import meteordevelopment.meteorclient.events.world.TickEvent;

import meteordevelopment.meteorclient.utils.player.ChatUtils;

import meteordevelopment.orbit.EventHandler;

import net.minecraft.client.network.PlayerListEntry;

import xbwk.addon.Elytraskyaddon;

import java.util.*;

public class Advertise extends Module {

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
    private final SettingGroup sgTabList = settings.createGroup("Tab List");

    // 消息设置
    private final Setting<CommandType> commandType = sgGeneral.add(new EnumSetting.Builder<CommandType>()
            .name("command-type")
            .description("Send specific message to all players in a server.")
            .defaultValue(CommandType.MSG)
            .build()
    );

    private final Setting<String> message = sgGeneral.add(new StringSetting.Builder()
            .name("message")
            .description("Message to send")
            .defaultValue("Join ElytraSky at qq group 1029533840, we will offer kits.")
            .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Delay in ticks (20tick=1s)")
            .defaultValue(100)
            .min(20)
            .sliderMin(20)
            .sliderMax(200)
            .build()
    );

    private final Setting<Boolean> randomize = sgGeneral.add(new BoolSetting.Builder()
            .name("randomize-order")
            .description("Randomize the order of players to message.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> excludeSelf = sgGeneral.add(new BoolSetting.Builder()
            .name("exclude-self")
            .description("Exclude yourself from the player list.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> autoRefreshAfterRound = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-refresh-after-round")
            .description("Automatically refresh player list after completing one round")
            .defaultValue(true)
            .build()
    );

    // Tab列表设置
    private final Setting<Boolean> useTabList = sgTabList.add(new BoolSetting.Builder()
            .name("use-tab-list")
            .description("Use players from Tab list")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> autoRefresh = sgTabList.add(new BoolSetting.Builder()
            .name("auto-refresh")
            .description("Automatically refresh player list during operation")
            .defaultValue(true)
            .visible(useTabList::get)
            .build()
    );

    private final Setting<Integer> refreshInterval = sgTabList.add(new IntSetting.Builder()
            .name("refresh-interval")
            .description("Refresh interval in ticks")
            .defaultValue(100)
            .min(20)
            .visible(() -> useTabList.get() && autoRefresh.get())
            .build()
    );

    // 手动玩家列表
    private final Setting<List<String>> manualPlayers = sgGeneral.add(new StringListSetting.Builder()
            .name("manual-players")
            .description("Manually added players")
            .defaultValue(new ArrayList<>())
            .visible(() -> !useTabList.get())
            .build()
    );

    private int timer = 0;
    private int refreshTimer = 0;
    private final List<String> playerList = new ArrayList<>();
    private int currentIndex = 0;
    private final Random random = new Random();
    private boolean isFirstRound = true;

    public Advertise() {
        super(Elytraskyaddon.CATEGORY, "advertise", "Automatically sends messages to players.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        refreshTimer = 0;
        currentIndex = 0;
        isFirstRound = true;
        updatePlayerList();
        info("Advertise module enabled. Targeting " + playerList.size() + " players.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;

// 刷新玩家列表
        if (useTabList.get() && autoRefresh.get()) {
            refreshTimer++;
            if (refreshTimer >= refreshInterval.get()) {
                updatePlayerList();
                refreshTimer = 0;
            }
        }

// 发送消息
        timer++;
        if (timer >= delay.get()) {
            sendNextMessage();
            timer = 0;
        }
    }

    private void updatePlayerList() {
        List<String> newPlayerList = new ArrayList<>();

        if (useTabList.get()) {
            // 从Tab列表获取玩家
            if (mc != null && mc.getNetworkHandler() != null) {
                for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                    String playerName = entry.getProfile().getName();
                    if (!excludeSelf.get() || !playerName.equals(mc.player.getGameProfile().getName())) {
                        newPlayerList.add(playerName);
                    }
                }
            }
        } else {
            // 使用手动添加的玩家
            newPlayerList.addAll(manualPlayers.get());
        }

// 只在玩家列表有变化时更新
        if (!newPlayerList.equals(playerList)) {
            playerList.clear();
            playerList.addAll(newPlayerList);

            if (randomize.get() && !playerList.isEmpty()) {
                Collections.shuffle(playerList);
            }

            currentIndex = 0;
            info("Player list updated: " + playerList.size() + " players");
        }
    }

    private void sendNextMessage() {
        if (playerList.isEmpty()) {
            info("Player list is empty");
            return;
        }

// 检查是否完成一轮
        if (currentIndex >= playerList.size()) {
            handleRoundCompletion();
            return;
        }

        String playerName = playerList.get(currentIndex);
        sendMessage(playerName);
        info("Sent message to: " + playerName + " (" + (currentIndex + 1) + "/" + playerList.size() + ")");

        currentIndex++;
    }

    private void handleRoundCompletion() {
        info("Completed one round of messaging to all " + playerList.size() + " players");

        if (autoRefreshAfterRound.get()) {
            // 自动刷新玩家列表
            updatePlayerList();
            info("Automatically refreshed player list for next round");
        } else {
            // 重置索引继续下一轮
            currentIndex = 0;
            if (randomize.get() && !playerList.isEmpty()) {
                Collections.shuffle(playerList);
            }
            info("Player List refreshed,starting again.");
        }

        isFirstRound = false;
    }

    private void sendMessage(String playerName) {
        String command = String.format("%s %s %s",
                commandType.get().toString(),
                playerName,
                message.get()
        );

        ChatUtils.sendPlayerMsg(command);
    }

    public void manualRefresh() {
        updatePlayerList();
        info("Manually refreshed player list: " + playerList.size() + " players");
    }

    public int getCurrentPlayerCount() {
        return playerList.size();
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public boolean isRoundComplete() {
        return currentIndex >= playerList.size();
    }

    public void startNewRound() {
        currentIndex = 0;
        if (randomize.get() && !playerList.isEmpty()) {
            Collections.shuffle(playerList);
        }
        info("Started new messaging round");
    }

}
