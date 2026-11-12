package com.livespectate;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 配置文件 {@code config/livespectate-common.toml} 的定义。
 *
 * <p>所有自动轮换相关键都直接位于文件顶层，键名与需求文档严格一致。
 * 由于取值都是惰性读取（每次使用时调用 {@code .get()}），
 * {@code /livespectate reload} 之后新值立即生效。
 */
public final class Config {

    public static final ModConfigSpec SPEC;

    // ------------------------------------------------------------------
    // 自动轮换
    // ------------------------------------------------------------------

    /** 未显式指定间隔时使用的默认间隔（秒）。 */
    public static final ModConfigSpec.IntValue AUTO_DEFAULT_INTERVAL_SECONDS;

    /** SEQUENTIAL 或 RANDOM。 */
    public static final ModConfigSpec.ConfigValue<String> AUTO_MODE;

    /** 是否允许把执行者自己作为轮换目标。 */
    public static final ModConfigSpec.BooleanValue AUTO_INCLUDE_SELF;

    /** 是否排除旁观模式玩家。 */
    public static final ModConfigSpec.BooleanValue AUTO_EXCLUDE_SPECTATORS;

    /** 是否排除创造模式玩家。 */
    public static final ModConfigSpec.BooleanValue AUTO_EXCLUDE_CREATIVE;

    /** 需要排除的玩家名列表（大小写不敏感）。 */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> AUTO_EXCLUDE_NAMES;

    /** 没有任何有效目标时是否自动停止轮换。 */
    public static final ModConfigSpec.BooleanValue AUTO_STOP_WHEN_NO_TARGETS;

    /** 手动 {@code /livespectate <玩家>} 是否覆盖（停止）该管理员的自动轮换。 */
    public static final ModConfigSpec.BooleanValue AUTO_MANUAL_OVERRIDES_AUTO;

    /** 是否允许跟随目标跨维度。 */
    public static final ModConfigSpec.BooleanValue AUTO_CROSS_DIMENSION_FOLLOW;

    // ------------------------------------------------------------------
    // 相机朝向
    // ------------------------------------------------------------------

    /**
     * 是否把相机账号<b>自身</b>的朝向同步成目标的朝向。
     *
     * <p>为什么需要：原版第三人称的摄像机角度取自"相机实体"，但第三方相机模组
     * （越肩视角 Shoulder Surfing 等）会把相机角度<b>解耦</b>出来、只跟玩家朝向走。
     * 如果模组只切换相机实体、不碰相机账号本身的朝向，切换目标后视镜就会停在原处
     * （表现为"人都换了，镜头还朝着上一个方向"）。开启后切换瞬间 + 周期性同步，
     * 两种相机模式都能保持"从目标后脑勺朝前看"。
     */
    public static final ModConfigSpec.BooleanValue CAMERA_LOCK_ROTATION;

    /** 朝向同步的检查间隔（tick）。只在偏差超过阈值时才真正发包。 */
    public static final ModConfigSpec.IntValue CAMERA_LOCK_INTERVAL_TICKS;

    // ------------------------------------------------------------------
    // 通用
    // ------------------------------------------------------------------

    /** 执行者退出游戏时是否立即恢复其游戏模式（防止崩服/退服后卡在旁观）。 */
    public static final ModConfigSpec.BooleanValue RESTORE_ON_LOGOUT;

    /** 消息语言：zh_cn 或 en_us。 */
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_LANGUAGE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        MESSAGE_LANGUAGE = builder
                .comment("消息语言 / message language: zh_cn 或 en_us",
                        "Mod 自带双语消息，不依赖客户端资源包。")
                .define("message-language", "zh_cn", o -> "zh_cn".equals(o) || "en_us".equals(o));

        RESTORE_ON_LOGOUT = builder
                .comment("管理员退出游戏时是否立刻恢复其原游戏模式。",
                        "开启可避免服务器重启/掉线后管理员卡在旁观模式。")
                .define("restore-on-logout", true);

        AUTO_DEFAULT_INTERVAL_SECONDS = builder
                .comment("自动轮换默认间隔（秒）。/livespectate auto start 不带参数时使用。")
                .defineInRange("auto-default-interval-seconds", 30, 1, 86_400);

        AUTO_MODE = builder
                .comment("自动轮换目标选择模式：SEQUENTIAL（按在线列表顺序循环）或 RANDOM（随机）。")
                .define("auto-mode", "SEQUENTIAL", o -> "SEQUENTIAL".equals(o) || "RANDOM".equals(o));

        AUTO_INCLUDE_SELF = builder
                .comment("自动轮换是否可以把执行者自己作为目标。")
                .define("auto-include-self", false);

        AUTO_EXCLUDE_SPECTATORS = builder
                .comment("自动轮换是否排除旁观模式玩家。")
                .define("auto-exclude-spectators", true);

        AUTO_EXCLUDE_CREATIVE = builder
                .comment("自动轮换是否排除创造模式玩家。")
                .define("auto-exclude-creative", false);

        AUTO_EXCLUDE_NAMES = builder
                .comment("自动轮换需要排除的玩家名列表，例如 [\"Steve\", \"Alex\"]。")
                .defineListAllowEmpty("auto-exclude-names", List.<String>of(), () -> "", o -> o instanceof String);

        AUTO_STOP_WHEN_NO_TARGETS = builder
                .comment("没有任何有效目标时：true = 自动停止轮换并提示；false = 保持等待。")
                .define("auto-stop-when-no-targets", true);

        AUTO_MANUAL_OVERRIDES_AUTO = builder
                .comment("执行 /livespectate <玩家> 时是否停止该管理员的自动轮换，避免手动与自动冲突。")
                .define("auto-manual-overrides-auto", true);

        AUTO_CROSS_DIMENSION_FOLLOW = builder
                .comment("自动轮换是否允许跟随目标切换维度。false 时只在同一维度内选择目标。")
                .define("auto-cross-dimension-follow", true);

        CAMERA_LOCK_ROTATION = builder
                .comment("是否把相机账号自身的朝向同步成目标的朝向。",
                        "开着时无论用原版第三人称还是越肩视角（Shoulder Surfing）等第三方相机模组，",
                        "镜头都会保持在目标后脑勺朝前看，切换目标时立即复位。",
                        "关掉则相机账号可以自由转头（手动观察用）。")
                .define("camera-lock-rotation", true);

        CAMERA_LOCK_INTERVAL_TICKS = builder
                .comment("朝向同步的检查间隔（tick，20 tick = 1 秒）。",
                        "只在朝向偏差超过 12° 时才发包，所以平时基本没有额外流量。")
                .defineInRange("camera-lock-interval-ticks", 10, 1, 200);

        SPEC = builder.build();
    }

    private Config() {
    }

    /** 当前配置的自动轮换模式。 */
    public static AutoMode mode() {
        return AutoMode.parse(AUTO_MODE.get());
    }

    /** 默认轮换间隔（秒），至少 1 秒。 */
    public static int defaultIntervalSeconds() {
        return Math.max(1, AUTO_DEFAULT_INTERVAL_SECONDS.get());
    }

    public static boolean isEnglish() {
        return "en_us".equalsIgnoreCase(MESSAGE_LANGUAGE.get());
    }
}
