package kadx.gui.cache.usage

import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode

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

	/**
	 * 惰性解析缓存（S7-c）。
	 *
	 * **为什么需要**：usage 缓存文件里的所有方法引用都指向一张**全局 intern 的
	 * `MthRef` 表**（[UsageFileAdapter] 读取时按下标复用同一实例），因此同一个
	 * `MthRef` 会被成千上万个 usage 列表共享。实测（有界微信子集，缓存命中路径）
	 * 9,682,150 次解析只对应 872,216 个不同实例 —— **91% 是重复解析，平均每对 11 次**，
	 * 而每次解析都要在 253k 项的 `rawClsMap` 上做一次随机访存（占 apply 采样 65.6%）。
	 *
	 * **为什么有效**：把结果缓存在实例上，重复引用退化为一次字段读（无哈希、无探针），
	 * 而任何以「键 -> 节点」形式的外置 memo 都仍需每元素一次哈希+探针，收益同量级于原开销。
	 *
	 * **线程安全**：`@Volatile` + 幂等。`applyForClass` 可能被并发调用，但同一个
	 * `(cls, shortId)` 必然解析到同一个节点，故竞态无害。
	 *
	 * **内存代价**：每个已解析实例多持有一个 `MethodNode` 引用（4–8 字节，
	 * 这些节点本身已存活于类树中，不额外延长其生命周期），872k 个约 3.5–7 MB。
	 *
	 * 注意：[equals]/[hashCode] 只依赖 [cls]/[shortId]（本类被用作 `HashMap` 键），
	 * 该字段不参与判等。
	 */
	@Volatile
	private var resolved: MethodNode? = null

	/** 解析本引用为方法节点；结果缓存，重复调用只做一次字段读。 */
	fun resolve(root: RootNode): MethodNode {
		val cached = resolved
		if (cached != null) {
			return cached
		}
		val node = root.resolveDirectMethod(cls, shortId)
		resolved = node
		return node
	}

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
