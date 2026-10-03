package jadx.cli.config

/**
 * jadx 配置对象的标记接口。
 *
 * **做什么**：仅用于给 [JadxConfigAdapter] 的泛型参数 `T` 加上约束，
 * 表示“可以被 Gson 读写为配置文件的类型”。
 *
 * **为什么保留为接口**：`JadxCLIArgs`（以及 jadx-gui 的 `JadxSettingsData`）都会实现它，
 * 保持原 Java 接口不变，Java 实现方无需改动。
 */
interface IJadxConfig
