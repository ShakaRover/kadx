package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.ILocalVar
import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType
import jadx.core.utils.Utils

/**
 * 局部变量调试信息属性：保存某个方法在 DEX 调试段里声明的全部局部变量。
 *
 * **用途**：`DebugInfoAttachVisitor` 解析完调试信息后，把整张局部变量表挂到方法上；
 * 后续 `DebugInfoApplyVisitor` 再按偏移把这些变量名/类型套用到具体寄存器上。
 *
 * **Kotlin 转换说明**：原 Java 只有 getter，这里直接声明为只读属性 [localVars]，
 * 生成的 `getLocalVars()` 与原 JVM 方法名一致，Java 调用方零改动。
 */
class LocalVarsDebugInfoAttr(val localVars: List<ILocalVar>) : IJadxAttribute {

	override val attrType: AType<LocalVarsDebugInfoAttr> get() = AType.LOCAL_VARS_DEBUG_INFO

	override fun toString(): String = "Debug Info:\n  " + Utils.listToString(localVars, "\n  ")
}
