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
 * 多行代码注释：注释文本中的换行应逐行输出为 `// ...`。
 */
class TestCodeCommentsMultiline : IntegrationTest() {

	@Test
	fun test() {
		printOffsets()

		val baseClsId = TestCodeCommentsMultilineFixture.TestCls::class.java.name
		val mthRef = KadxNodeRef(RefType.METHOD, baseClsId, "test(Z)I")
		val insnRef: IJavaCodeRef = KadxCodeRef.forInsn(if (isJavaInput()) 15 else 11)
		val insnComment: ICodeComment = KadxCodeComment(mthRef, insnRef, "multi\nline\ncomment")

		val codeData = KadxCodeData()
		codeData.setComments(listOf(insnComment))
		getArgs().codeData = codeData

		assertThat(getClassNode(TestCodeCommentsMultilineFixture.TestCls::class.java))
			.code()
			.containsOne("// multi")
			.containsOne("// line")
			.containsOne("// comment")
	}
}
