package jadx.tests.integration.others

import jadx.api.data.ICodeComment
import jadx.api.data.IJavaCodeRef
import jadx.api.data.IJavaNodeRef.RefType
import jadx.api.data.impl.JadxCodeComment
import jadx.api.data.impl.JadxCodeData
import jadx.api.data.impl.JadxCodeRef
import jadx.api.data.impl.JadxNodeRef
import jadx.core.dex.nodes.ClassNode
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 代码注释：类 / 内部类 / 字段 / 方法 / 指令注释都应出现在反编译结果中，且可动态更新。
 */
class TestCodeComments : IntegrationTest() {

	@Test
	fun test() {
		val baseClsId = TestCodeCommentsFixture.TestCls::class.java.name
		val clsComment: ICodeComment = JadxCodeComment(JadxNodeRef.forCls(baseClsId), "class comment")
		val innerClsComment: ICodeComment = JadxCodeComment(JadxNodeRef.forCls(baseClsId + "\$A"), "inner class comment")
		val fldComment: ICodeComment = JadxCodeComment(JadxNodeRef(RefType.FIELD, baseClsId, "intField:I"), "field comment")
		val mthRef = JadxNodeRef(RefType.METHOD, baseClsId, "test()I")
		val mthComment: ICodeComment = JadxCodeComment(mthRef, "method comment")
		val insnRef: IJavaCodeRef = JadxCodeRef.forInsn(if (isJavaInput()) 13 else 11)
		val insnComment: ICodeComment = JadxCodeComment(mthRef, insnRef, "insn comment")

		val codeData = JadxCodeData()
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

		val updInsnComment: ICodeComment = JadxCodeComment(mthRef, insnRef, "updated insn comment")
		codeData.setComments(listOf(updInsnComment))
		jadxDecompiler.reloadCodeData()
		assertThat(cls)
			.reloadCode(this)
			.containsOne("System.out.println(\"comment\"); // updated insn comment")
			.doesNotContain("class comment")
			.containsOne(" comment")
	}
}
