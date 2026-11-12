package com.livespectate.client;

import com.livespectate.network.LiveSpectateNetwork;
import com.livespectate.network.SpectateStatusPacket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

/**
 * F9 控制面板（Forge 1.20.1 版）。
 *
 * <p>面板只做两件事：把服务端状态显示出来（SimpleChannel 同步）、
 * 把按钮点击翻译成 {@code /livespectate ...} 命令发给服务端。
 */
public final class LiveSpectateScreen extends Screen {

    private static final int PANEL_W = 352;
    private static final int PANEL_H = 250;

    /** 记住上次填的间隔，关掉面板再开还在。 */
    private static String rememberedInterval = "30";

    private int left;
    private int top;
    private int panelW;
    private int panelH;

    private EditBox intervalBox;
    private PlayerListWidget playerList;
    private String listSignature = "";
    private int refreshTicker;

    public LiveSpectateScreen() {
        super(Component.literal("LiveSpectate"));
    }

    // ------------------------------------------------------------------
    // 布局
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        this.panelW = Math.min(PANEL_W, this.width - 12);
        this.panelH = Math.min(PANEL_H, this.height - 12);
        this.left = (this.width - this.panelW) / 2;
        this.top = (this.height - this.panelH) / 2;

        int contentY = this.top + 56;
        int listX = this.left + 8;
        int listW = Math.max(80, this.panelW - 140);
        int listH = Math.max(40, this.panelH - 56 - 58);
        // 1.20.1：构造参数是 (mc, width, height, y0, y1, itemHeight)，横向位置用 setLeftPos
        this.playerList = new PlayerListWidget(this.minecraft, listW, listH, contentY, contentY + listH, 14);
        this.playerList.setLeftPos(listX);
        this.addRenderableWidget(this.playerList);

        int buttonX = this.left + this.panelW - 124;
        int buttonW = 116;
        int rowHeight = 22;

        this.addRenderableWidget(Button.builder(
                        label("开始自动轮换", "Start auto rotation"),
                        b -> command("livespectate auto start " + intervalValue()))
                .bounds(buttonX, contentY, buttonW, 20).build());

        this.intervalBox = new EditBox(this.font, buttonX + 48, contentY + rowHeight + 1, buttonW - 48, 18,
                label("间隔秒数", "Interval"));
        this.intervalBox.setValue(rememberedInterval);
        this.intervalBox.setMaxLength(6);
        this.intervalBox.setFilter(text -> text.isEmpty() || text.matches("\\d{1,6}"));
        this.intervalBox.setResponder(text -> rememberedInterval = text);
        this.addRenderableWidget(this.intervalBox);

        this.addRenderableWidget(Button.builder(
                        label("停止自动轮换", "Stop auto rotation"),
                        b -> command("livespectate auto stop"))
                .bounds(buttonX, contentY + rowHeight * 2, buttonW, 20).build());

        this.addRenderableWidget(Button.builder(
                        label("下一个目标", "Next target"),
                        b -> command("livespectate auto next"))
                .bounds(buttonX, contentY + rowHeight * 3, buttonW, 20).build());

        this.addRenderableWidget(Button.builder(
                        label("退出附体", "Stop spectating"),
                        b -> command("livespectate stop"))
                .bounds(buttonX, contentY + rowHeight * 4, buttonW, 20).build());

        this.addRenderableWidget(Button.builder(
                        label("重载配置", "Reload config"),
                        b -> command("livespectate reload"))
                .bounds(buttonX, contentY + rowHeight * 5, buttonW, 20).build());

        int bottomY = this.top + this.panelH - 30;
        int centerX = this.left + this.panelW / 2;
        this.addRenderableWidget(Button.builder(
                        label("刷新状态", "Refresh"),
                        b -> requestStatus())
                .bounds(centerX - 84, bottomY, 80, 20).build());
        this.addRenderableWidget(Button.builder(
                        label("关闭", "Close"),
                        b -> this.onClose())
                .bounds(centerX + 4, bottomY, 80, 20).build());

        this.refreshPlayerList(true);
        this.requestStatus();
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (++this.refreshTicker >= 20) {
            this.refreshTicker = 0;
            this.requestStatus();
            this.refreshPlayerList(false);
        }
    }

    /** 打开面板不应该暂停游戏（单人里当相机机位用时尤其重要）。 */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics graphics) {
        super.renderBackground(graphics);
        graphics.fill(this.left - 1, this.top - 1, this.left + this.panelW + 1, this.top + this.panelH + 1, 0xFF3A3A3A);
        graphics.fill(this.left, this.top, this.left + this.panelW, this.top + this.panelH, 0xF0121212);
        graphics.fill(this.left, this.top + 20, this.left + this.panelW, this.top + 21, 0xFF4A4A4A);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font,
                ClientText.t("LiveSpectate 控制面板", "LiveSpectate Control Panel"),
                this.left + this.panelW / 2, this.top + 6, 0xFFFFFF);

        SpectateStatusPacket status = ClientSpectateState.latest();
        int statusX = this.left + 8;
        int statusY = this.top + 22;

        if (status == null) {
            graphics.drawString(this.font,
                    ClientText.t("等待服务端状态…", "Waiting for server status..."),
                    statusX, statusY, 0xA0A0A0);
        } else {
            String target = status.attached() && !status.target().isEmpty()
                    ? status.target()
                    : ClientText.t("未附体", "none");
            graphics.drawString(this.font,
                    ClientText.t("附体目标：", "Spectating: ") + target,
                    statusX, statusY, status.attached() ? 0x55FF55 : 0xA0A0A0);

            String auto = status.auto()
                    ? ClientText.t("开启", "ON") + " · " + status.intervalSeconds() + "s"
                    : ClientText.t("关闭", "OFF");
            String next = status.next().isEmpty() ? ClientText.t("无", "none") : status.next();
            graphics.drawString(this.font,
                    ClientText.t("自动轮换：", "Auto: ") + auto
                            + ClientText.t("    下一个：", "    Next: ") + next,
                    statusX, statusY + 11, status.auto() ? 0xFFD479 : 0xA0A0A0);
        }

        graphics.drawString(this.font, ClientText.t("间隔(秒)", "Every(s)"),
                this.left + this.panelW - 124, this.top + 56 + 22 + 6, 0xA0A0A0);

        String playerCount = ClientText.t("在线玩家", "Online players");
        if (this.playerList != null) {
            playerCount = playerCount + " (" + this.playerList.rowCount() + ")";
        }
        graphics.drawString(this.font, playerCount, this.left + 8, this.top + 44, 0x808080);

        int hintY = this.top + this.panelH - 52;
        if (ClientSpectateState.ageMillis() > 3000L) {
            graphics.drawString(this.font,
                    ClientText.t("未收到服务端状态：服务器也需要安装本模组",
                            "No server status: the server must have this mod installed"),
                    this.left + 8, hintY, 0xFF5555);
        } else {
            graphics.drawString(this.font,
                    ClientText.t("左键点击玩家名即可附体 · 需要 OP 等级 2",
                            "Left-click a player to spectate · Requires OP level 2"),
                    this.left + 8, hintY, 0x808080);
        }
        graphics.drawString(this.font,
                ClientText.t("操作结果会显示在聊天栏", "Results are printed in chat"),
                this.left + 8, hintY + 11, 0x606060);
    }

    // ------------------------------------------------------------------
    // 动作
    // ------------------------------------------------------------------

    /** 面板文案 -> 组件（按钮/输入框要的是 Component）。 */
    private static Component label(String chinese, String english) {
        return Component.literal(ClientText.t(chinese, english));
    }

    private String intervalValue() {
        String raw = this.intervalBox == null ? "" : this.intervalBox.getValue().trim();
        if (raw.isEmpty()) {
            return String.valueOf(30);
        }
        try {
            int seconds = Integer.parseInt(raw);
            return String.valueOf(Math.max(1, Math.min(86400, seconds)));
        } catch (NumberFormatException e) {
            return "30";
        }
    }

    /** 面板上的所有操作都翻译成原版命令，服务端那边做真正的校验。 */
    private void command(String command) {
        ClientPacketListener connection = this.minecraft.getConnection();
        if (connection != null) {
            connection.sendCommand(command);
        }
        this.requestStatus();
    }

    private void requestStatus() {
        try {
            LiveSpectateNetwork.requestStatus();
        } catch (Exception ignored) {
            // 服务端没装本模组时可能发不出去，面板会显示"未收到服务端状态"
        }
    }

    // ------------------------------------------------------------------
    // 在线玩家列表
    // ------------------------------------------------------------------

    private void refreshPlayerList(boolean force) {
        if (this.playerList == null) {
            return;
        }
        ClientPacketListener connection = this.minecraft.getConnection();
        if (connection == null) {
            return;
        }

        List<PlayerInfo> players = new ArrayList<>(connection.getOnlinePlayers());
        players.sort(Comparator.comparing(info -> info.getProfile().getName(), String.CASE_INSENSITIVE_ORDER));

        StringBuilder signature = new StringBuilder();
        for (PlayerInfo info : players) {
            signature.append(info.getProfile().getName()).append(':').append(info.getGameMode()).append(';');
        }
        if (!force && signature.toString().equals(this.listSignature)) {
            return;
        }
        this.listSignature = signature.toString();

        this.playerList.clearRows();
        for (PlayerInfo info : players) {
            this.playerList.addPlayer(info);
        }
    }

    private boolean isCurrentTarget(String name) {
        SpectateStatusPacket status = ClientSpectateState.latest();
        return status != null && status.attached() && name.equalsIgnoreCase(status.target());
    }

    private final class PlayerListWidget extends ObjectSelectionList<PlayerListWidget.Row> {

        PlayerListWidget(Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight) {
            super(minecraft, width, height, y0, y1, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return Math.max(40, this.getWidth() - 12);
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getRight() - 6;
        }

        void clearRows() {
            this.clearEntries();
        }

        void addPlayer(PlayerInfo info) {
            this.addEntry(new Row(info));
        }

        int rowCount() {
            return this.getItemCount();
        }

        final class Row extends ObjectSelectionList.Entry<Row> {

            private final String name;
            private final GameType gameType;
            private final boolean self;

            Row(PlayerInfo info) {
                this.name = info.getProfile().getName();
                this.gameType = info.getGameMode();
                this.self = LiveSpectateScreen.this.minecraft.player != null
                        && LiveSpectateScreen.this.minecraft.player.getUUID().equals(info.getProfile().getId());
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovering, float partialTick) {
                boolean currentTarget = isCurrentTarget(this.name);
                int color = currentTarget ? 0xFF55FF55 : hovering ? 0xFFFFFFA0 : 0xFFE0E0E0;
                String label = this.name + (this.self ? ClientText.t("（自己）", " (you)") : "");
                graphics.drawString(LiveSpectateScreen.this.font, label, left + 4, top + 3, color);

                String tag = gameModeTag(this.gameType);
                int tagStart = left + width - LiveSpectateScreen.this.font.width(tag) - 4;
                if (!tag.isEmpty()) {
                    graphics.drawString(LiveSpectateScreen.this.font, tag, tagStart, top + 3,
                            gameModeColor(this.gameType));
                }
                if (currentTarget) {
                    String marker = ClientText.t("◀ 当前", "< current");
                    int markerX = left + 6 + LiveSpectateScreen.this.font.width(label);
                    if (markerX + LiveSpectateScreen.this.font.width(marker) < tagStart) {
                        graphics.drawString(LiveSpectateScreen.this.font, marker, markerX, top + 3, 0xFF55FF55);
                    }
                }
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) {
                    command("livespectate " + this.name);
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return Component.literal(this.name);
            }
        }
    }

    private static String gameModeTag(GameType gameType) {
        if (gameType == null) {
            return "";
        }
        return switch (gameType) {
            case SPECTATOR -> ClientText.t("旁观", "SPEC");
            case CREATIVE -> ClientText.t("创造", "CREA");
            case ADVENTURE -> ClientText.t("冒险", "ADV");
            default -> ClientText.t("生存", "SURV");
        };
    }

    private static int gameModeColor(GameType gameType) {
        if (gameType == null) {
            return 0x808080;
        }
        return switch (gameType) {
            case SPECTATOR -> 0x808080;
            case CREATIVE -> 0x55FFFF;
            case ADVENTURE -> 0xFFAA00;
            default -> 0xAAAAAA;
        };
    }
}
