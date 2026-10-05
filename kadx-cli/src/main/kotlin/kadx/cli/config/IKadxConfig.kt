package kadx.cli.config

/**
 * kadx 配置对象的标记接口。
 *
 * **做什么**：仅用于给 [KadxConfigAdapter] 的泛型参数 `T` 加上约束，
 * 表示“可以被 Gson 读写为配置文件的类型”。
 *
 * **为什么保留为接口**：`KadxCLIArgs`（以及 kadx-gui 的 `KadxSettingsData`）都会实现它，
 * 保持原 Java 接口不变，Java 实现方无需改动。
 */
interface IKadxConfig
