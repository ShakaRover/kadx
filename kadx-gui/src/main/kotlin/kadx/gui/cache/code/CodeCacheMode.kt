package kadx.gui.cache.code

import kadx.gui.utils.NLS

/**
 * 代码缓存模式。
 *
 * **做什么**：决定反编译出的代码字符串存放在哪里：
 * - [MEMORY]：仅内存（最快，但进程退出即丢失）；缓存在 [kadx.api.impl.BoundedMemoryCodeCache]
 *   中有容量上限，不会随全量扫描无限增长；
 * - [DISK_WITH_CACHE]：磁盘 + 内存字符串缓存（兼顾速度与持久化）；
 * - [DISK]：仅磁盘（内存占用最低，也是设置默认值）。
 *
 * **为什么保留显式 `getLocalizedName()/getDesc()`**：这两个方法名被设置界面以
 * `toString()` / getter 方式使用，保留函数形态可让 Java 调用方零改动；
 * 字段本身声明为 `private val`，避免与函数名产生 JVM 签名冲突。
 */
enum class CodeCacheMode(
	private val label: String,
	private val desc: String,
) {
	MEMORY(NLS.str("preferences.codeCacheMode.memory"), NLS.str("preferences.codeCacheMode.memory.desc")),
	DISK_WITH_CACHE(
		NLS.str("preferences.codeCacheMode.diskWithCache"),
		NLS.str("preferences.codeCacheMode.diskWithCache.desc"),
	),
	DISK(NLS.str("preferences.codeCacheMode.disk"), NLS.str("preferences.codeCacheMode.disk.desc")),
	;

	/** 本地化的模式名称（用于下拉框显示）。 */
	val localizedName: String get() = label

	/** 本地化的模式说明（用于提示文本）。 */
	fun getDesc(): String = desc

	override fun toString(): String = localizedName

	companion object {
		/** 拼接所有模式的“名称 - 说明”文本，用作设置项的 tooltip。 */
		fun buildToolTip(): String = values().joinToString("\n") { v -> v.localizedName + " - " + v.getDesc() }
	}
}
