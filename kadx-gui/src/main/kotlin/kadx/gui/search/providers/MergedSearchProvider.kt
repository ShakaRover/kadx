package kadx.gui.search.providers

import kadx.gui.jobs.Cancelable
import kadx.gui.search.ISearchProvider
import kadx.gui.treemodel.JNode

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

	val isEmpty: Boolean get() = list.isEmpty()

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
			if (cancelable.isCanceled) {
				// 取消/暂停：**保留 current**，续跑时从同一个子提供者继续。
				// 原来这里把 current 置为 -1（等同于“已耗尽”），会让用户按一次 Stop 之后
				// 的“加载更多”对类/方法/字段搜索永久返回空。
				return null
			}
			current++
			if (current >= list.size) {
				// 真正搜完
				current = -1
				return null
			}
		}
	}

	override fun progress(): Int = list.sumOf { it.progress() }

	override fun total(): Int = total
}
