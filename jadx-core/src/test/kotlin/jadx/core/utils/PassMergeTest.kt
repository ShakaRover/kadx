package jadx.core.utils

import jadx.api.impl.passes.DecompilePassWrapper
import jadx.api.plugins.pass.JadxPass
import jadx.api.plugins.pass.JadxPassInfo
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo
import jadx.api.plugins.pass.impl.SimpleJadxPassInfo
import jadx.api.plugins.pass.types.JadxDecompilePass
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.dex.visitors.IDexTreeVisitor
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test

class PassMergeTest {

	@Test
	fun testSimple() {
		val base = listOf("a", "b", "c")
		check(base, mockPass("x"), listOf("a", "b", "c", "x"))
		check(base, mockPass(mockInfo("x").after(JadxPassInfo.START)), listOf("x", "a", "b", "c"))
		check(base, mockPass(mockInfo("x").before(JadxPassInfo.END)), listOf("a", "b", "c", "x"))
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
		assertThat(thrown).isInstanceOf(JadxRuntimeException::class.java)
	}

	private fun check(visitorNames: List<String>, pass: JadxPass, result: List<String>) {
		check(visitorNames, listOf(pass), result)
	}

	private fun check(visitorNames: List<String>, passes: List<JadxPass>, result: List<String>) {
		val visitors = visitorNames.map { mockVisitor(it) }.toMutableList()
		PassMerge(visitors).merge(passes) { p -> DecompilePassWrapper(p as JadxDecompilePass) }
		val resultVisitors = visitors.map { it.getName() }
		assertThat(resultVisitors).isEqualTo(result)
	}

	private fun mockVisitor(name: String): IDexTreeVisitor = object : AbstractVisitor() {
		override fun getName(): String = name
	}

	private fun mockPass(name: String): JadxPass = mockPass(SimpleJadxPassInfo(name))

	private fun mockInfo(name: String): OrderedJadxPassInfo = OrderedJadxPassInfo(name, name)

	private fun mockPass(info: JadxPassInfo): JadxPass = object : JadxDecompilePass {
		override fun init(root: RootNode) {
		}

		override fun visit(cls: ClassNode): Boolean = false

		override fun visit(mth: MethodNode) {
		}

		override fun getInfo(): JadxPassInfo = info

		override fun toString(): String = info.getName()
	}
}
