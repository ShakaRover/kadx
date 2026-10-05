package kadx.tests.functional

import kadx.core.dex.instructions.IfNode
import kadx.core.dex.instructions.IfOp
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.LiteralArg
import kadx.core.dex.regions.conditions.IfCondition
import kadx.core.dex.regions.conditions.IfCondition.Mode
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [IfCondition] 的归一化、合并与化简（not/and/or）语义校验。
 */
class TestIfCondition {

	@Test
	fun testNormalize() {
		// 'a != false' => 'a == true'
		val a = mockArg()
		val c = makeCondition(IfOp.NE, a, LiteralArg.litFalse())
		val simp = IfCondition.simplify(c)

		assertThat(simp.mode).isEqualTo(Mode.COMPARE)
		val compare = checkNotNull(simp.compare)
		assertThat(compare.a).isEqualTo(a)
		assertThat(compare.b).isEqualTo(LiteralArg.litTrue())
	}

	@Test
	fun testMerge() {
		val a = makeSimpleCondition()
		val b = makeSimpleCondition()
		val c = IfCondition.merge(Mode.OR, a, b)

		assertThat(c.mode).isEqualTo(Mode.OR)
		assertThat(c.first()).isEqualTo(a)
		assertThat(c.second()).isEqualTo(b)
	}

	@Test
	fun testSimplifyNot() {
		// !(!a) => a
		val a = IfCondition.not(IfCondition.not(makeSimpleCondition()))
		assertThat(IfCondition.simplify(a)).isEqualTo(a)
	}

	@Test
	fun testSimplifyNot2() {
		// !(!a) => a
		val a = IfCondition.not(makeNegCondition())
		assertThat(IfCondition.simplify(a)).isEqualTo(a)
	}

	@Test
	fun testSimplify() {
		// '!(!a || !b)' => 'a && b'
		val a = makeSimpleCondition()
		val b = makeSimpleCondition()
		val c = IfCondition.not(IfCondition.merge(Mode.OR, IfCondition.not(a), IfCondition.not(b)))
		val simp = IfCondition.simplify(c)

		assertThat(simp.mode).isEqualTo(Mode.AND)
		assertThat(simp.first()).isEqualTo(a)
		assertThat(simp.second()).isEqualTo(b)
	}

	@Test
	fun testSimplify2() {
		// '(!a || !b) && !c' => '!((a && b) || c)'
		val a = makeSimpleCondition()
		val b = makeSimpleCondition()
		val c = makeSimpleCondition()
		val cond = IfCondition.merge(Mode.AND, IfCondition.merge(Mode.OR, IfCondition.not(a), IfCondition.not(b)), IfCondition.not(c))
		val simp = IfCondition.simplify(cond)

		assertThat(simp.mode).isEqualTo(Mode.NOT)
		val f = simp.first()
		assertThat(f.mode).isEqualTo(Mode.OR)
		assertThat(f.first().mode).isEqualTo(Mode.AND)
		assertThat(f.first().first()).isEqualTo(a)
		assertThat(f.first().second()).isEqualTo(b)
		assertThat(f.second()).isEqualTo(c)
	}

	private fun makeCondition(op: IfOp, a: InsnArg, b: InsnArg): IfCondition = IfCondition.fromIfNode(IfNode(op, -1, a, b))

	private fun makeSimpleCondition(): IfCondition = makeCondition(IfOp.EQ, mockArg(), LiteralArg.litTrue())

	private fun makeNegCondition(): IfCondition = makeCondition(IfOp.NE, mockArg(), LiteralArg.litTrue())

	private fun mockArg(): InsnArg = InsnArg.reg(0, ArgType.INT)
}
