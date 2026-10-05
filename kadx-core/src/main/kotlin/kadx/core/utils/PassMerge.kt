package kadx.core.utils

import kadx.api.plugins.pass.KadxPass
import kadx.api.plugins.pass.KadxPassInfo
import kadx.core.dex.visitors.IDexTreeVisitor
import kadx.core.utils.exceptions.KadxRuntimeException
import java.util.ArrayList
import java.util.Comparator
import java.util.HashMap
import java.util.IdentityHashMap

/**
 * 把插件自定义 Pass 合并进内置 visitor 列表，并按声明的 runBefore/runAfter 依赖排序。
 *
 * **用途**：插件可以通过 [KadxPassInfo] 声明自己相对内置 pass 的执行顺序，
 * 这里做拓扑定位并插入到正确位置。
 */
class PassMerge(private val visitors: MutableList<IDexTreeVisitor>) {

	private var mergePassesNames: Set<String> = emptySet()
	private var namesMap: MutableMap<IDexTreeVisitor, String> = HashMap()

	fun merge(customPasses: List<KadxPass>?, wrap: (KadxPass) -> IDexTreeVisitor) {
		if (Utils.isEmpty(customPasses)) {
			return
		}
		val mergePasses = ArrayList(ListUtils.map(customPasses) { p -> MergePass(p, wrap(p), p.getInfo()) })
		linkDeps(mergePasses)
		mergePasses.sortWith(ExtDepsComparator(visitors).thenComparing(InvertedDepsComparator.INSTANCE))

		namesMap = IdentityHashMap()
		visitors.forEach { p -> namesMap[p] = p.getName() }
		mergePasses.forEach { p -> namesMap[p.visitor] = p.name }

		mergePassesNames = mergePasses.map { it.name }.toSet()

		for (mergePass in mergePasses) {
			val pos = searchInsertPos(mergePass)
			if (pos == -1) {
				visitors.add(mergePass.visitor)
			} else {
				visitors.add(pos, mergePass.visitor)
			}
		}
	}

	private fun searchInsertPos(pass: MergePass): Int {
		val runAfter = pass.after()
		val runBefore = pass.before()
		if (runAfter.isEmpty() && runBefore.isEmpty()) {
			return -1 // 最后
		}
		if (ListUtils.isSingleElement(runAfter, KadxPassInfo.START)) {
			return 0
		}
		if (ListUtils.isSingleElement(runBefore, KadxPassInfo.END)) {
			return -1
		}
		val visitorsCount = visitors.size
		val namePosMap = HashMap<String, Int>(visitorsCount)
		for (i in 0 until visitorsCount) {
			namePosMap[checkNotNull(namesMap[visitors[i]])] = i
		}
		var after = -1
		for (name in runAfter) {
			val pos = namePosMap[name]
			if (pos != null) {
				after = Math.max(after, pos)
			} else {
				if (mergePassesNames.contains(name)) {
					// 已知 pass 忽略
					continue
				}
				throw KadxRuntimeException(
					"Ordering pass not found: $name, listed in 'runAfter' of pass: $pass" +
						"\n all passes: " + ListUtils.map(visitors) { namesMap[it] },
				)
			}
		}
		var before = Int.MAX_VALUE
		for (name in runBefore) {
			val pos = namePosMap[name]
			if (pos != null) {
				before = Math.min(before, pos)
			} else {
				if (mergePassesNames.contains(name)) {
					// 已知 pass 忽略
					continue
				}
				throw KadxRuntimeException(
					"Ordering pass not found: $name, listed in 'runBefore' of pass: $pass" +
						"\n all passes: " + ListUtils.map(visitors) { namesMap[it] },
				)
			}
		}
		if (before <= after) {
			throw KadxRuntimeException(
				"Conflict order requirements for pass: $pass" +
					"\n run after: " + runAfter +
					"\n run before: " + runBefore +
					"\n passes: " + ListUtils.map(visitors) { namesMap[it] },
			)
		}
		if (after == -1) {
			if (before == Int.MAX_VALUE) {
				// 无顺序要求，放最后
				return -1
			}
			return before
		}
		val pos = after + 1
		return if (pos >= visitorsCount) -1 else pos
	}

	/**
	 * 把依赖关系做成双向的：A 声明 runAfter B，则在 B 的 before 列表里加上 A。
	 */
	private fun linkDeps(mergePasses: List<MergePass>) {
		val map = mergePasses.associateBy { p -> p.name }
		for (pass in mergePasses) {
			for (after in pass.info.runAfter()) {
				val beforePass = map[after]
				if (beforePass != null) {
					beforePass.before().add(pass.name)
				}
			}
			for (before in pass.info.runBefore()) {
				val afterPass = map[before]
				if (afterPass != null) {
					afterPass.after().add(pass.name)
				}
			}
		}
	}

	private class MergePass(
		val pass: KadxPass,
		val visitor: IDexTreeVisitor,
		val info: KadxPassInfo,
	) {
		// 复制依赖列表，便于后续修改
		private val beforeList: MutableList<String> = ArrayList(info.runBefore())
		private val afterList: MutableList<String> = ArrayList(info.runAfter())

		val name: String get() = info.getName()

		fun before(): MutableList<String> = beforeList

		fun after(): MutableList<String> = afterList

		override fun toString(): String = info.getName()
	}

	/**
	 * 有 visitor 依赖的 pass 排前面。
	 */
	private class ExtDepsComparator(visitors: List<IDexTreeVisitor>) : Comparator<MergePass> {
		private val names: Set<String> = visitors.map { it.getName() }.toSet()

		override fun compare(first: MergePass, second: MergePass): Int {
			val isFirst = containsVisitor(first.before()) || containsVisitor(first.after())
			val isSecond = containsVisitor(second.before()) || containsVisitor(second.after())
			return -isFirst.compareTo(isSecond)
		}

		private fun containsVisitor(deps: List<String>): Boolean {
			for (dep in deps) {
				if (names.contains(dep)) {
					return true
				}
			}
			return false
		}
	}

	/**
	 * 反转依赖顺序：若 pass 依赖另一个 pass，则把自己排在它前面。
	 */
	private class InvertedDepsComparator : Comparator<MergePass> {
		override fun compare(first: MergePass, second: MergePass): Int {
			if (first.before().contains(second.name) ||
				first.after().contains(second.name)
			) {
				return 1
			}
			if (second.before().contains(first.name) ||
				second.after().contains(first.name)
			) {
				return -1
			}
			return 0
		}

		companion object {
			val INSTANCE: InvertedDepsComparator = InvertedDepsComparator()
		}
	}
}
