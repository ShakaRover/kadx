package kadx.core.dex.attributes.nodes

import kadx.core.utils.InsnUtils

/**
 * 跳转信息：记录一条指令内部跳转的源偏移与目标偏移。
 *
 * **用途**：主要给“跳转表/分支”类指令记录其内部跳转目标，用于区域分析。
 * 实现了值语义的 [equals] / [hashCode]，可以放进集合去重。
 */
class JumpInfo(
	val src: Int,
	val dest: Int,
) {

	override fun hashCode(): Int = 31 * dest + src

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other == null) {
			return false
		}
		if (javaClass != other.javaClass) {
			return false
		}
		val o = other as JumpInfo
		return dest == o.dest && src == o.src
	}

	override fun toString(): String = "JUMP: " + InsnUtils.formatOffset(src) + " -> " + InsnUtils.formatOffset(dest)
}
