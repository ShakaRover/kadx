package kadx.tests.integration.others

import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.args.InsnWrapArg
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.utils.BlockUtils
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 验证重复的 'check-cast' 指令：这是 javac 的 bug 产生的
 * （http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6246854），
 * kadx 应将其合并为单条 RETURN + 包装的 CHECK_CAST。
 */
class TestDuplicateCast : IntegrationTest() {

	@Test
	fun test() {
		val cls: ClassNode = getClassNode(TestDuplicateCastFixture.TestCls::class.java)
		val mth: MethodNode = getMethod(cls, "method")

		assertThat(cls)
			.code()
			.contains("return (int[]) o;")

		val insns: List<InsnNode> = BlockUtils.collectAllInsns(checkNotNull(mth.basicBlocks))
		assertThat(insns).hasSize(1)
		val insnNode = insns[0]
		assertThat(insnNode.type).isEqualTo(InsnType.RETURN)
		assertThat(insnNode.getArg(0).isInsnWrap).isTrue()
		val wrapInsn = (insnNode.getArg(0) as InsnWrapArg).wrapInsn
		assertThat(wrapInsn.type).isEqualTo(InsnType.CHECK_CAST)
		assertThat(wrapInsn.getArg(0).isInsnWrap).isFalse()
	}
}
