package kadx.gui.search.providers

import kadx.gui.jobs.Cancelable
import kadx.gui.search.ISearchProvider
import kadx.gui.treemodel.JClass
import kadx.gui.treemodel.JNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.Icon

/**
 * [MergedSearchProvider] 的游标/续跑语义测试。
 *
 * **背景**：`SearchDialog.loadMoreResults` 复用同一个 `SearchTask` 与同一批 provider 实例
 * （`prepare()` 只在新建搜索时调用），所以“加载更多”本身就是**从游标续跑**，不会重跑整库。
 *
 * 但原实现在**取消时**把 `current` 置为 `-1`（等同于“已耗尽”），而“取消”恰恰是分页与
 * 用户按 Stop 都会触发的信号。后果：用户按一次 Stop 后，“加载更多”对
 * 类/方法/字段搜索**永远返回空**。
 *
 * 这些测试用固定假数据源，确定性验证：① 取消不破坏游标；② 分页续扫结果 == 一次整跑结果。
 */
class MergedSearchProviderTest {

	private class FakeCancelable : Cancelable {
		@Volatile
		override var isCanceled: Boolean = false

		override fun cancel() {
			isCanceled = true
		}
	}

	/** 固定假数据源：产出 `$prefix0..$prefix(count-1)`；被取消时返回 null 但保留游标。 */
	private class FakeProvider(private val prefix: String, private val count: Int) : ISearchProvider {
		private var idx = 0

		override fun next(cancelable: Cancelable): JNode? {
			if (cancelable.isCanceled || idx >= count) {
				return null
			}
			return FakeNode("$prefix${idx++}")
		}

		override fun progress(): Int = idx

		override fun total(): Int = count
	}

	private class FakeNode(private val label: String) : JNode() {
		override fun getJParent(): JClass? = null

		override fun makeString(): String = label

		override fun getIcon(): Icon? = null

		override fun toString(): String = label
	}

	private fun newMerged(): MergedSearchProvider = MergedSearchProvider().apply {
		add(FakeProvider("c", 3))
		add(FakeProvider("m", 2))
		prepare()
	}

	@Test
	fun cancelDuringNextKeepsCursor() {
		val merged = newMerged()
		val cancelable = FakeCancelable()

		assertThat(merged.next(cancelable).toString()).isEqualTo("c0")

		// 用户按 Stop：取消标志置位，且此时正处于 next() 调用中（子提供者观察到取消后返回 null）
		cancelable.cancel()
		assertThat(merged.next(cancelable)).isNull()

		// “加载更多”：resetCancel 之后必须从 c1 继续，而不是因为游标被置 -1 而永久为空
		cancelable.isCanceled = false
		assertThat(merged.next(cancelable).toString()).isEqualTo("c1")
	}

	@Test
	fun pagedResumeMatchesSingleRun() {
		val singleRun = ArrayList<String>()
		val mergedForSingle = newMerged()
		val c1 = FakeCancelable()
		while (true) {
			val node = mergedForSingle.next(c1) ?: break
			singleRun.add(node.toString())
		}
		assertThat(singleRun).containsExactly("c0", "c1", "c2", "m0", "m1")

		// 分页：每页取 2 条即“达上限取消”，然后续跑，直到取空
		val paged = ArrayList<String>()
		val merged = newMerged()
		val cancelable = FakeCancelable()
		repeat(10) {
			cancelable.isCanceled = false
			var taken = 0
			while (taken < 2) {
				val node = merged.next(cancelable) ?: return@repeat
				paged.add(node.toString())
				taken++
			}
			cancelable.cancel()
		}

		assertThat(paged).isEqualTo(singleRun)
	}

	@Test
	fun exhaustedProviderStaysExhausted() {
		val merged = newMerged()
		val cancelable = FakeCancelable()
		repeat(5) { merged.next(cancelable) }

		assertThat(merged.next(cancelable)).isNull()
		// 真正搜完后仍是空（不能因为修了取消语义就变成死循环/重复产出）
		assertThat(merged.next(cancelable)).isNull()
	}
}
