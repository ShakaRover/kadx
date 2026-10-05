package kadx.api.metadata.impl

import kadx.api.metadata.ICodeAnnotation
import kadx.api.metadata.ICodeAnnotation.AnnType
import kadx.api.metadata.ICodeMetadata
import kadx.api.metadata.ICodeNodeRef
import kadx.api.metadata.annotations.NodeDeclareRef
import kadx.core.utils.Utils
import java.util.Collections
import java.util.Comparator
import java.util.NavigableMap
import java.util.TreeMap
import java.util.function.BiFunction

/**
 * [ICodeMetadata] 的默认实现。
 *
 * **数据结构**：
 * - [lines]：生成行号 -> dex 调试行号；
 * - [navMap]：字符位置 -> 代码注解，**按位置倒序**存放（因此 `tailMap` 是向更小位置走，
 *   `headMap` 是向更大位置走）。倒序是为了让 `higherEntry` 等查找更直观。
 *
 * **Kotlin 转换说明**：静态工厂 `build` / `empty` 放入 `companion object` 并加
 * `@JvmStatic`；构造器保持私有。[getAsMap] 保留原来的协变返回类型 `NavigableMap`
 * （`NavigableMap` 没有 Kotlin 对应类型，必须显式引用 `java.util`）。
 */
class CodeMetadataStorage private constructor(
	private val lines: Map<Int, Int>,
	private val navMap: NavigableMap<Int, ICodeAnnotation>,
) : ICodeMetadata {

	companion object {
		/** 由行号映射与注解映射构建；两者都为空时直接复用 [ICodeMetadata.EMPTY]。 */
		@JvmStatic
		fun build(lines: Map<Int, Int>, map: Map<Int, ICodeAnnotation>): ICodeMetadata {
			if (map.isEmpty() && lines.isEmpty()) {
				return ICodeMetadata.EMPTY
			}
			// 位置倒序，使 higherEntry 对应“位置更小”的方向
			val reverseCmp = Comparator.reverseOrder<Int>()
			val navMap = TreeMap<Int, ICodeAnnotation>(reverseCmp)
			navMap.putAll(map)
			return CodeMetadataStorage(lines, navMap)
		}

		/** 空元数据。 */
		@JvmStatic
		fun empty(): ICodeMetadata = CodeMetadataStorage(emptyMap(), Collections.emptyNavigableMap())
	}

	override fun getAt(position: Int): ICodeAnnotation? = navMap[position]

	override fun getClosestUp(position: Int): ICodeAnnotation? = navMap.higherEntry(position)?.value

	override fun searchUp(position: Int, annType: AnnType): ICodeAnnotation? {
		for (v in navMap.tailMap(position, true).values) {
			if (v.annType === annType) {
				return v
			}
		}
		return null
	}

	override fun searchUp(position: Int, limitPos: Int, annType: AnnType): ICodeAnnotation? {
		for (v in navMap.subMap(position, true, limitPos, true).values) {
			if (v.annType === annType) {
				return v
			}
		}
		return null
	}

	override fun <T> searchUp(startPos: Int, visitor: BiFunction<Int, ICodeAnnotation, T>): T? {
		for (entry in navMap.tailMap(startPos, true).entries) {
			val value = visitor.apply(entry.key, entry.value)
			if (value != null) {
				return value
			}
		}
		return null
	}

	override fun <T> searchDown(startPos: Int, visitor: BiFunction<Int, ICodeAnnotation, T>): T? {
		val map = navMap.headMap(startPos, true).descendingMap()
		for (entry in map.entries) {
			val value = visitor.apply(entry.key, entry.value)
			if (value != null) {
				return value
			}
		}
		return null
	}

	override fun getNodeAt(position: Int): ICodeNodeRef? {
		var nesting = 0
		for (ann in navMap.tailMap(position, true).values) {
			when (ann.annType) {
				AnnType.END -> nesting++

				AnnType.DECLARATION -> {
					val node = (ann as NodeDeclareRef).getNode()
					val nodeType = node.annType
					if (nodeType === AnnType.CLASS || nodeType === AnnType.METHOD) {
						if (nesting == 0) {
							return node
						}
						nesting--
					}
				}

				else -> {
					// 其它注解不参与嵌套计数
				}
			}
		}
		return null
	}

	override fun getNodeBelow(position: Int): ICodeNodeRef? {
		for (ann in navMap.headMap(position, true).descendingMap().values) {
			if (ann.annType === AnnType.DECLARATION) {
				val node = (ann as NodeDeclareRef).getNode()
				val nodeType = node.annType
				if (nodeType === AnnType.CLASS || nodeType === AnnType.METHOD) {
					return node
				}
			}
		}
		return null
	}

	override fun getAsMap(): NavigableMap<Int, ICodeAnnotation> = navMap

	override fun getLineMapping(): Map<Int, Int> = lines

	override fun toString(): String = "CodeMetadata{\nlines=" + lines +
		"\nannotations=\n " + Utils.listToString(navMap.descendingMap().entries, "\n ") + "\n}"
}
