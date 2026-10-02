package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType

/**
 * 循环标签属性：把一个 [LoopInfo] 关联到某条指令上，用于代码生成时输出 `loopN:` 标签。
 *
 * **用途**：当循环中存在 `continue`/`break` 需要跨层跳转时，必须给目标循环起一个标签，
 * 该属性就负责记录“这条指令属于哪个循环的标签点”。
 *
 * **Kotlin 转换说明**：[loop] 声明为只读属性，生成的 `getLoop()` 与原 JVM 方法名一致。
 */
class LoopLabelAttr(val loop: LoopInfo) : IJadxAttribute {

	override fun getAttrType(): AType<LoopLabelAttr> = AType.LOOP_LABEL

	override fun toString(): String = "LOOP_LABEL: $loop"
}
