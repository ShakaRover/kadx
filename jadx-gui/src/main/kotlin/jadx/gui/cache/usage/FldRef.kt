package jadx.gui.cache.usage

/**
 * 字段引用（usage 缓存内部的轻量标识）。
 *
 * **做什么**：记录“字段所属类的原始名 + 字段短 id”，供 [FldUsageData] 引用。
 *
 * **注意**：原 Java 未重写 `equals`/`hashCode`，本类保持引用语义（不实现 equals），
 * 不要改成 `data class`。
 */
internal class FldRef(
	val cls: String,
	val shortId: String,
)
