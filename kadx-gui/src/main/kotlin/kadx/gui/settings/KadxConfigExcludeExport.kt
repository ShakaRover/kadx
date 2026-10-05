package kadx.gui.settings

/**
 * 标记“从配置导出/复制中排除”的字段。
 *
 * **做什么**：`KadxSettings.exportSettingsString()` 在导出配置时会跳过带本注解的字段，
 * 从而避免把环境相关状态（最近文件、窗口位置等）写入导出结果。
 *
 * **为什么保留为 Java 注解形态**：原 Java 是 `@Target(FIELD)` 的注解，Gson/导出逻辑
 * 通过 `Field.getAnnotation(...)` 读取；Kotlin 的 `annotation class` 编译后同样是 Java 注解，
 * 保留 `FIELD` 目标即可让注解落在 Kotlin 属性的背后字段上。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class KadxConfigExcludeExport
