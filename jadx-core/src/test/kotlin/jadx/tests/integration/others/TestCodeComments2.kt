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
 * 代码注释（常量返回值）：为 return 指令添加注释，注释应紧跟在 return 语句之后。
 */
class TestCodeComments2 : IntegrationTest() {

	@Test
	fun test() {
		printOffsets()

		val baseClsId = TestCodeComments2Fixture.TestCls::class.java.name
		val mthRef = JadxNodeRef(RefType.METHOD, baseClsId, "test(Z)I")
		val insnRef: IJavaCodeRef = JadxCodeRef.forInsn(if (isJavaInput()) 13 else 10)
		val insnComment: ICodeComment = JadxCodeComment(mthRef, insnRef, "return comment")
		val insnRef2: IJavaCodeRef = JadxCodeRef.forInsn(if (isJavaInput()) 15 else 11)
		val insnComment2: ICodeComment = JadxCodeComment(mthRef, insnRef2, "another return comment")

		val codeData = JadxCodeData()
		codeData.setComments(listOf(insnComment, insnComment2))
		getArgs().codeData = codeData

		assertThat(getClassNode(TestCodeComments2Fixture.TestCls::class.java))
			.decompile()
			.checkCodeOffsets()
			.code()
			.containsOne("return 1; // " + insnComment.getComment())
			.containsOne("return 3; // " + insnComment2.getComment())
	}
}
