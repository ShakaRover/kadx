package kadx.tests.integration.others

import kadx.api.data.ICodeComment
import kadx.api.data.IJavaNodeRef.RefType
import kadx.api.data.impl.KadxCodeComment
import kadx.api.data.impl.KadxCodeData
import kadx.api.data.impl.KadxNodeRef
import kadx.core.dex.nodes.ClassNode
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 接口方法与实现方法的注释：@Override 与两处注释都应保留。
 */
class TestCodeCommentsOverride : IntegrationTest() {

	@Test
	fun test() {
		val baseClsId = TestCodeCommentsOverrideFixture.TestCls::class.java.name
		val iMthRef = KadxNodeRef(RefType.METHOD, baseClsId + "\$I", "mth()V")
		val iMthComment: ICodeComment = KadxCodeComment(iMthRef, "interface mth comment")

		val mthRef = KadxNodeRef(RefType.METHOD, baseClsId + "\$A", "mth()V")
		val mthComment: ICodeComment = KadxCodeComment(mthRef, "mth comment")

		val codeData = KadxCodeData()
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
