package kadx.core.utils

import kadx.api.impl.passes.DecompilePassWrapper
import kadx.api.plugins.pass.KadxPass
import kadx.api.plugins.pass.KadxPassInfo
import kadx.api.plugins.pass.impl.OrderedKadxPassInfo
import kadx.api.plugins.pass.impl.SimpleKadxPassInfo
import kadx.api.plugins.pass.types.KadxDecompilePass
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.core.dex.visitors.AbstractVisitor
import kadx.core.dex.visitors.IDexTreeVisitor
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test

class PassMergeTest {

	@Test
	fun testSimple() {
		val base = listOf("a", "b", "c")
		check(base, mockPass("x"), listOf("a", "b", "c", "x"))
		check(base, mockPass(mockInfo("x").after(KadxPassInfo.START)), listOf("x", "a", "b", "c"))
		check(base, mockPass(mockInfo("x").before(KadxPassInfo.END)), listOf("a", "b", "c", "x"))
	}

	@Test
	fun testSingle() {
		val base = listOf("a", "b", "c")
		check(base, mockPass(mockInfo("x").after("a")), listOf("a", "x", "b", "c"))
		check(base, mockPass(mockInfo("x").before("c")), listOf("a", "b", "x", "c"))
		check(base, mockPass(mockInfo("x").before("a")), listOf("x", "a", "b", "c"))
		check(base, mockPass(mockInfo("x").after("c")), listOf("a", "b", "c", "x"))
	}

	@Test
	fun testMulti() {
		val base = listOf("a", "b", "c")
		val x = mockPass(mockInfo("x").after("a"))
		val y = mockPass(mockInfo("y").after("a"))
		val z = mockPass(mockInfo("z").before("b"))
		check(base, listOf(x, y, z), listOf("a", "y", "x", "z", "b", "c"))
	}

	@Test
	fun testMultiWithDeps() {
		val base = listOf("a", "b", "c")
		val x = mockPass(mockInfo("x").after("a"))
		val y = mockPass(mockInfo("y").after("x"))
		val z = mockPass(mockInfo("z").before("b").after("y"))
		check(base, listOf(x, y, z), listOf("a", "x", "y", "z", "b", "c"))
	}

	@Test
	fun testMultiWithDeps2() {
		val base = listOf("a", "b", "c")
		val x = mockPass(mockInfo("x").before("y"))
		val y = mockPass(mockInfo("y").before("b"))
		val z = mockPass(mockInfo("z").after("y"))
		check(base, listOf(x, y, z), listOf("a", "x", "y", "z", "b", "c"))
	}

	@Test
	fun testMultiWithDeps3() {
		val base = listOf("a", "b", "c")
		val x = mockPass(mockInfo("x"))
		val y = mockPass(mockInfo("y").after("x").before("b"))
		check(base, listOf(x, y), listOf("a", "x", "y", "b", "c"))
	}

	@Test
	fun testLoop() {
		val base = listOf("a", "b", "c")
		val x = mockPass(mockInfo("x").before("y"))
		val y = mockPass(mockInfo("y").before("x"))
		val thrown = catchThrowable { check(base, listOf(x, y), emptyList()) }
		assertThat(thrown).isInstanceOf(KadxRuntimeException::class.java)
	}

	private fun check(visitorNames: List<String>, pass: KadxPass, result: List<String>) {
		check(visitorNames, listOf(pass), result)
	}

	private fun check(visitorNames: List<String>, passes: List<KadxPass>, result: List<String>) {
		val visitors = visitorNames.map { mockVisitor(it) }.toMutableList()
		PassMerge(visitors).merge(passes) { p -> DecompilePassWrapper(p as KadxDecompilePass) }
		val resultVisitors = visitors.map { it.getName() }
		assertThat(resultVisitors).isEqualTo(result)
	}

	private fun mockVisitor(name: String): IDexTreeVisitor = object : AbstractVisitor() {
		override fun getName(): String = name
	}

	private fun mockPass(name: String): KadxPass = mockPass(SimpleKadxPassInfo(name))

	private fun mockInfo(name: String): OrderedKadxPassInfo = OrderedKadxPassInfo(name, name)

	private fun mockPass(info: KadxPassInfo): KadxPass = object : KadxDecompilePass {
		override fun init(root: RootNode) {
		}

		override fun visit(cls: ClassNode): Boolean = false

		override fun visit(mth: MethodNode) {
		}

		override fun getInfo(): KadxPassInfo = info

		override fun toString(): String = info.getName()
	}
}
