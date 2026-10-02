package jadx.api.resources

/**
 * 资源内容类型：用来区分某个资源文件的内容是文本、二进制、无内容还是未知。
 *
 * **做什么**：GUI 搜索/展示资源时，需要知道资源能否按文本读取（例如文本资源才能做内容搜索）。
 *
 * **为什么这样写**：这是公共 API，枚举常量名与顺序必须与原 Java 完全一致
 * （jadx-cli / jadx-gui / 插件都会引用这些常量），因此转换时不改名、不合并。
 */
enum class ResourceContentType {
	CONTENT_TEXT,
	CONTENT_BINARY,
	CONTENT_NONE,
	CONTENT_UNKNOWN,
}
