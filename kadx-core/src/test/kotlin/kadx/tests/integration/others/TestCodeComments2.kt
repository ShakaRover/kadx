package kadx.tests.integration.others

import kadx.api.data.ICodeComment
import kadx.api.data.IJavaCodeRef
import kadx.api.data.IJavaNodeRef.RefType
import kadx.api.data.impl.KadxCodeComment
import kadx.api.data.impl.KadxCodeData
import kadx.api.data.impl.KadxCodeRef
import kadx.api.data.impl.KadxNodeRef
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 代码注释（常量返回值）：为 return 指令添加注释，注释应紧跟在 return 语句之后。
 */
class TestCodeComments2 : IntegrationTest() {

	@Test
	fun test() {
		printOffsets()

		val baseClsId = TestCodeComments2Fixture.TestCls::class.java.name
		val mthRef = KadxNodeRef(RefType.METHOD, baseClsId, "test(Z)I")
		val insnRef: IJavaCodeRef = KadxCodeRef.forInsn(if (isJavaInput()) 13 else 10)
		val insnComment: ICodeComment = KadxCodeComment(mthRef, insnRef, "return comment")
		val insnRef2: IJavaCodeRef = KadxCodeRef.forInsn(if (isJavaInput()) 15 else 11)
		val insnComment2: ICodeComment = KadxCodeComment(mthRef, insnRef2, "another return comment")

		val codeData = KadxCodeData()
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
