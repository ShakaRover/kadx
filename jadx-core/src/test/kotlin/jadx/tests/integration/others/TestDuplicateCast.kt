package jadx.tests.integration.others

import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.BlockUtils
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 验证重复的 'check-cast' 指令：这是 javac 的 bug 产生的
 * （http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6246854），
 * jadx 应将其合并为单条 RETURN + 包装的 CHECK_CAST。
 */
class TestDuplicateCast : IntegrationTest() {

	@Test
	fun test() {
		val cls: ClassNode = getClassNode(TestDuplicateCastFixture.TestCls::class.java)
		val mth: MethodNode = getMethod(cls, "method")

		assertThat(cls)
			.code()
			.contains("return (int[]) o;")

		val insns: List<InsnNode> = BlockUtils.collectAllInsns(checkNotNull(mth.getBasicBlocks()))
		assertThat(insns).hasSize(1)
		val insnNode = insns[0]
		assertThat(insnNode.getType()).isEqualTo(InsnType.RETURN)
		assertThat(insnNode.getArg(0).isInsnWrap).isTrue()
		val wrapInsn = (insnNode.getArg(0) as InsnWrapArg).wrapInsn
		assertThat(wrapInsn.getType()).isEqualTo(InsnType.CHECK_CAST)
		assertThat(wrapInsn.getArg(0).isInsnWrap).isFalse()
	}
}
