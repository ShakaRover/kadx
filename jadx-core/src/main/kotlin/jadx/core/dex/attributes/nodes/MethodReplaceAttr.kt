package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.PinnedAttribute
import jadx.core.dex.attributes.AType
import jadx.core.dex.nodes.MethodNode

/**
 * 方法替换属性：指示对该方法的调用应替换为 [replaceMth]。
 *
 * **用途**：合成方法重定向（synthetic method redirect）——调用 A 方法时，实际输出对 B 的调用。
 *
 * **Kotlin 转换说明**：[replaceMth] 声明为只读属性，生成的 `getReplaceMth()` 与原 JVM 方法名一致。
 */
class MethodReplaceAttr(val replaceMth: MethodNode) : PinnedAttribute() {

	override val attrType: AType<MethodReplaceAttr> get() = AType.METHOD_REPLACE

	override fun toString(): String = "REPLACED_BY: $replaceMth"
}
