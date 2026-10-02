package jadx.tests.integration.others

import jadx.api.data.ICodeComment
import jadx.api.data.IJavaCodeRef
import jadx.api.data.IJavaNodeRef.RefType
import jadx.api.data.impl.JadxCodeComment
import jadx.api.data.impl.JadxCodeData
import jadx.api.data.impl.JadxCodeRef
import jadx.api.data.impl.JadxNodeRef
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 代码注释（随机返回值）：为 return 指令添加注释，反编译输出应包含对应注释文本。
 */
class TestCodeComments2a : IntegrationTest() {

	@Test
	fun test() {
		printOffsets()

		val baseClsId = TestCodeComments2aFixture.TestCls::class.java.name
		val mthRef = JadxNodeRef(RefType.METHOD, baseClsId, "test(Z)I")
		val insnRef: IJavaCodeRef = JadxCodeRef.forInsn(if (isJavaInput()) 22 else 18)
		val insnComment: ICodeComment = JadxCodeComment(mthRef, insnRef, "return comment")
		val insnRef2: IJavaCodeRef = JadxCodeRef.forInsn(if (isJavaInput()) 27 else 19)
		val insnComment2: ICodeComment = JadxCodeComment(mthRef, insnRef2, "another return comment")

		val codeData = JadxCodeData()
		codeData.setComments(listOf(insnComment, insnComment2))
		getArgs().codeData = codeData

		assertThat(getClassNode(TestCodeComments2aFixture.TestCls::class.java))
			.decompile()
			.checkCodeOffsets()
			.code()
			.containsOne("// " + insnComment.getComment())
			.containsOne("// " + insnComment2.getComment())
	}
}
