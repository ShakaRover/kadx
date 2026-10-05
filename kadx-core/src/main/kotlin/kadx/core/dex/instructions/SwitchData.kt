package kadx.core.dex.instructions

import kadx.api.plugins.input.insns.custom.ISwitchPayload
import kadx.core.dex.nodes.InsnNode
import kadx.core.utils.InsnUtils

/**
 * `packed-switch` / `sparse-switch` 的跳转表数据块。
 *
 * [keys] 是各分支的键值，[targets] 是与之一一对应的目标偏移。
 * 解析完成后，[fixTargets] 会把相对偏移修正为绝对偏移。
 *
 * Kotlin 转换说明：[size] / [keys] / [targets] 声明为属性，
 * 其 JVM getter 名与 Java 的 `getSize()` / `getKeys()` / `getTargets()` 一致。
 */
class SwitchData(payload: ISwitchPayload) : InsnNode(InsnType.SWITCH_DATA, 0) {

	val size: Int = payload.size
	val keys: IntArray = payload.keys
	val targets: IntArray = payload.targets

	/** 把相对跳转偏移加上 switch 指令自身偏移，得到绝对偏移。 */
	fun fixTargets(switchOffset: Int) {
		val size = this.size
		val targets = this.targets
		for (i in 0 until size) {
			targets[i] += switchOffset
		}
	}

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append("switch-data {")
		for (i in 0 until size) {
			sb.append(keys[i]).append("->").append(InsnUtils.formatOffset(targets[i])).append(", ")
		}
		sb.append('}')
		appendAttributes(sb)
		return sb.toString()
	}
}
