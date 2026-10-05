package kadx.core.dex.visitors.typeinference

import kadx.core.dex.instructions.InsnType
import java.util.EnumMap

/**
 * 指令类型 → 类型监听器 的注册表。
 *
 * **算法意图**：类型传播时，一条指令的某个参数类型变化会触发该指令的监听器，
 * 由监听器决定如何把类型“顺带”传播给同指令的其它参数。
 *
 * **Kotlin 转换说明**：用 [EnumMap] 保持与 Java 相同的存储结构；
 * [getListenersForInsn] 在没有注册时返回空列表。
 */
class TypeUpdateRegistry {

	private val listenersMap: MutableMap<InsnType, MutableList<ITypeListener>> = EnumMap(InsnType::class.java)

	fun add(insnType: InsnType, listener: ITypeListener) {
		listenersMap.getOrPut(insnType) { ArrayList(3) }.add(listener)
	}

	fun getListenersForInsn(insnType: InsnType): List<ITypeListener> = listenersMap[insnType] ?: emptyList()
}
