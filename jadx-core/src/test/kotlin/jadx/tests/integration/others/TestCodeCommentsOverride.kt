package jadx.tests.integration.others

import jadx.api.data.ICodeComment
import jadx.api.data.IJavaNodeRef.RefType
import jadx.api.data.impl.JadxCodeComment
import jadx.api.data.impl.JadxCodeData
import jadx.api.data.impl.JadxNodeRef
import jadx.core.dex.nodes.ClassNode
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 接口方法与实现方法的注释：@Override 与两处注释都应保留。
 */
class TestCodeCommentsOverride : IntegrationTest() {

	@Test
	fun test() {
		val baseClsId = TestCodeCommentsOverrideFixture.TestCls::class.java.name
		val iMthRef = JadxNodeRef(RefType.METHOD, baseClsId + "\$I", "mth()V")
		val iMthComment: ICodeComment = JadxCodeComment(iMthRef, "interface mth comment")

		val mthRef = JadxNodeRef(RefType.METHOD, baseClsId + "\$A", "mth()V")
		val mthComment: ICodeComment = JadxCodeComment(mthRef, "mth comment")

		val codeData = JadxCodeData()
		codeData.setComments(listOf(iMthComment, mthComment))
		getArgs().codeData = codeData

		val cls: ClassNode = getClassNode(TestCodeCommentsOverrideFixture.TestCls::class.java)
		assertThat(cls)
			.decompile()
			.checkCodeOffsets()
			.code()
			.containsOne("@Override")
			.containsOne("// " + iMthComment.getComment())
			.containsOne("// " + mthComment.getComment())

		assertThat(cls)
			.reloadCode(this)
			.containsOne("@Override")
			.containsOne("// " + iMthComment.getComment())
			.containsOne("// " + mthComment.getComment())
	}
}
