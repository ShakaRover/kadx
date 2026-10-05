package kadx.core.dex.attributes.nodes

import kadx.api.CommentsLevel
import kadx.api.plugins.input.data.attributes.IKadxAttrType
import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.IAttributeNode
import kadx.core.utils.Utils
import java.util.Collections
import java.util.EnumMap

/**
 * KADX 注释属性：收集反编译过程中产生的诊断注释（错误/警告/信息/调试）。
 *
 * **按级别分组**：同一条注释可能重复出现，用 `Set` 去重；输出时按级别过滤，
 * 并且同一级别内按字符串排序，保证结果稳定可复现。
 *
 * **Kotlin 转换说明**：
 * - 静态 [add] 放入 companion + [JvmStatic]，Java 调用方仍写 `KadxCommentsAttr.add(...)`；
 * - `formatAndFilter` 原用 Java Stream，这里用等价的 Kotlin 集合操作（filter/flatMap/sorted），
 *   语义完全一致。
 */
class KadxCommentsAttr : IKadxAttribute {

	companion object {
		/** 向节点追加一条指定级别的注释（属性不存在时自动创建） */
		fun add(node: IAttributeNode, level: CommentsLevel, comment: String) {
			initFor(node).add(level, comment)
		}

		private fun initFor(node: IAttributeNode): KadxCommentsAttr {
			val currentAttr = node.get(AType.KADX_COMMENTS)
			if (currentAttr != null) {
				return currentAttr
			}
			val newAttr = KadxCommentsAttr()
			node.addAttr(newAttr)
			return newAttr
		}
	}

	val comments: MutableMap<CommentsLevel, MutableSet<String>> =
		EnumMap<CommentsLevel, MutableSet<String>>(CommentsLevel::class.java)

	fun add(level: CommentsLevel, comment: String) {
		comments.getOrPut(level) { HashSet() }.add(comment)
	}

	/** 按级别过滤并格式化注释；NONE / USER_ONLY 级别不输出任何注释 */
	fun formatAndFilter(level: CommentsLevel): List<String> {
		if (level == CommentsLevel.NONE || level == CommentsLevel.USER_ONLY) {
			return Collections.emptyList()
		}
		return comments.entries
			.filter { it.key.filter(level) }
			.flatMap { e -> e.value.map { v -> "KADX " + e.key.name + ": " + v } }
			.sorted()
	}

	override val attrType: IKadxAttrType<KadxCommentsAttr> get() = AType.KADX_COMMENTS

	override fun toString(): String = "KadxCommentsAttr{\n " +
		Utils.listToString(comments.entries, "\n ") { e ->
			e.key.toString() + ": \n -> " + Utils.listToString(e.value, "\n -> ")
		} + '}'
}
