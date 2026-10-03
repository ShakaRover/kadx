package jadx.gui.search.providers

import jadx.gui.jobs.Cancelable
import jadx.gui.search.ISearchProvider
import jadx.gui.treemodel.JNode

/**
 * 顺序合并多个搜索提供者。
 *
 * **做什么**：按加入顺序依次执行子提供者；当前子提供者搜完后再切到下一个。
 * 这样“类名/方法名/字段名”这类快速任务可以顺序跑完，避免并行开销。
 *
 * **为什么不是 `data class`**：它持有子提供者列表与游标，按身份使用。
 */
class MergedSearchProvider : ISearchProvider {

	private val list: MutableList<ISearchProvider> = ArrayList()
	private var current = 0
	private var total = 0

	fun add(provider: ISearchProvider) {
		list.add(provider)
	}

	fun isEmpty(): Boolean = list.isEmpty()

	/** 开始搜索前重置游标并汇总总量。 */
	fun prepare() {
		current = if (list.isEmpty()) -1 else 0
		total = list.sumOf { it.total() }
	}

	override fun next(cancelable: Cancelable): JNode? {
		if (current == -1) {
			return null
		}
		while (true) {
			val next = list[current].next(cancelable)
			if (next != null) {
				return next
			}
			current++
			if (current >= list.size || cancelable.isCanceled()) {
				// 搜索完成
				current = -1
				return null
			}
		}
	}

	override fun progress(): Int = list.sumOf { it.progress() }

	override fun total(): Int = total
}
