package kadx.gui.cache.usage

/**
 * 方法引用（usage 缓存内部的轻量标识）。
 *
 * **做什么**：只记录“方法所属类的原始名 + 方法短 id”，用于在 usage 数据中互相引用，
 * 避免直接持有 [kadx.core.dex.nodes.MethodNode] 造成大量对象常驻内存。
 *
 * **为什么不是 `data class`**：本类在 [UsageFileAdapter] 中作为 `HashMap` 的键，
 * 需要按“类名 + 短 id”的值语义判等；原 Java 手写了 `equals`/`hashCode`，
 * 这里原样保留（`data class` 会额外生成 `componentN`/`copy`，语义上没必要）。
 */
internal class MthRef(
	val cls: String,
	val shortId: String,
) {

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is MthRef) {
			return false
		}
		return cls == other.cls && shortId == other.shortId
	}

	override fun hashCode(): Int = 31 * cls.hashCode() + shortId.hashCode()
}
