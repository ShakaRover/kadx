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
 * 多行代码注释：注释文本中的换行应逐行输出为 `// ...`。
 */
class TestCodeCommentsMultiline : IntegrationTest() {

	@Test
	fun test() {
		printOffsets()

		val baseClsId = TestCodeCommentsMultilineFixture.TestCls::class.java.name
		val mthRef = JadxNodeRef(RefType.METHOD, baseClsId, "test(Z)I")
		val insnRef: IJavaCodeRef = JadxCodeRef.forInsn(if (isJavaInput()) 15 else 11)
		val insnComment: ICodeComment = JadxCodeComment(mthRef, insnRef, "multi\nline\ncomment")

		val codeData = JadxCodeData()
		codeData.setComments(listOf(insnComment))
		getArgs().codeData = codeData

		assertThat(getClassNode(TestCodeCommentsMultilineFixture.TestCls::class.java))
			.code()
			.containsOne("// multi")
			.containsOne("// line")
			.containsOne("// comment")
	}
}
