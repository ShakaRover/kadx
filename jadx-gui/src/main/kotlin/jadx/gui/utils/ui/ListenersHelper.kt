package jadx.gui.utils.ui

import java.util.IdentityHashMap
import java.util.function.BiConsumer
import javax.swing.event.CaretListener
import javax.swing.text.JTextComponent

/**
 * 监听器注册/注销管理器。
 *
 * **做什么**：按“组件 → 监听器列表”记录已注册的监听器，支持一次性移除全部或某个组件的监听器，
 * 避免重复注册导致的监听器泄漏。
 *
 * **为什么用 [IdentityHashMap]**：组件按对象身份（而非 `equals`）区分，与原 Java 行为一致。
 *
 * @param C 组件类型
 * @param L 监听器类型
 */
class ListenersHelper<C, L> private constructor(
	private val addMth: BiConsumer<C, L>,
	private val removeMth: BiConsumer<C, L>,
) {

	private val listenerMap: MutableMap<C, MutableList<L>> = IdentityHashMap()

	/** 注册监听器并记录，便于后续统一移除。 */
	@Synchronized
	fun add(component: C, listener: L) {
		addMth.accept(component, listener)
		listenerMap.getOrPut(component) { ArrayList() }.add(listener)
	}

	/** 移除并清空所有已记录的监听器。 */
	@Synchronized
	fun removeAll() {
		listenerMap.forEach { (comp, list) ->
			for (l in list) {
				remove(comp, l)
			}
		}
		listenerMap.clear()
	}

	/** 移除某个组件上的全部监听器。 */
	@Synchronized
	fun removeFor(component: C) {
		val list = listenerMap[component]
		if (list != null) {
			list.forEach { l -> remove(component, l) }
			listenerMap.remove(component)
		}
	}

	private fun remove(component: C, listener: L) {
		removeMth.accept(component, listener)
	}

	companion object {
		/** 构造一个用于 [JTextComponent] 光标监听的管理器。 */
		@JvmStatic
		fun buildForCaretListener(): ListenersHelper<JTextComponent, CaretListener> = ListenersHelper(JTextComponent::addCaretListener, JTextComponent::removeCaretListener)
	}
}
