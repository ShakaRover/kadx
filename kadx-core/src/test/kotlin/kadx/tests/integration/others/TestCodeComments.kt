package kadx.tests.integration.others

import kadx.api.data.ICodeComment
import kadx.api.data.IJavaCodeRef
import kadx.api.data.IJavaNodeRef.RefType
import kadx.api.data.impl.KadxCodeComment
import kadx.api.data.impl.KadxCodeData
import kadx.api.data.impl.KadxCodeRef
import kadx.api.data.impl.KadxNodeRef
import kadx.core.dex.nodes.ClassNode
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 代码注释：类 / 内部类 / 字段 / 方法 / 指令注释都应出现在反编译结果中，且可动态更新。
 */
class TestCodeComments : IntegrationTest() {

	@Test
	fun test() {
		val baseClsId = TestCodeCommentsFixture.TestCls::class.java.name
		val clsComment: ICodeComment = KadxCodeComment(KadxNodeRef.forCls(baseClsId), "class comment")
		val innerClsComment: ICodeComment = KadxCodeComment(KadxNodeRef.forCls(baseClsId + "\$A"), "inner class comment")
		val fldComment: ICodeComment = KadxCodeComment(KadxNodeRef(RefType.FIELD, baseClsId, "intField:I"), "field comment")
		val mthRef = KadxNodeRef(RefType.METHOD, baseClsId, "test()I")
		val mthComment: ICodeComment = KadxCodeComment(mthRef, "method comment")
		val insnRef: IJavaCodeRef = KadxCodeRef.forInsn(if (isJavaInput()) 13 else 11)
		val insnComment: ICodeComment = KadxCodeComment(mthRef, insnRef, "insn comment")

		val codeData = KadxCodeData()
		getArgs().codeData = codeData
		codeData.setComments(listOf(clsComment, innerClsComment, fldComment, mthComment, insnComment))

		val cls: ClassNode = getClassNode(TestCodeCommentsFixture.TestCls::class.java)
		assertThat(cls)
			.decompile()
			.checkCodeOffsets()
			.code()
			.containsOne("// class comment")
			.containsOne("// inner class comment")
			.containsOne("// field comment")
			.containsOne("// method comment")
			.containsOne("System.out.println(\"comment\"); // insn comment")

		val code = cls.getCode().codeStr
		assertThat(cls)
			.reloadCode(this)
			.isEqualTo(code)

		val updInsnComment: ICodeComment = KadxCodeComment(mthRef, insnRef, "updated insn comment")
		codeData.setComments(listOf(updInsnComment))
		kadxDecompiler.reloadCodeData()
		assertThat(cls)
			.reloadCode(this)
			.containsOne("System.out.println(\"comment\"); // updated insn comment")
			.doesNotContain("class comment")
			.containsOne(" comment")
	}
}
