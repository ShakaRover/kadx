package kadx.tests.integration.code

import kadx.api.data.CommentStyle
import kadx.api.data.ICodeComment
import kadx.api.data.IJavaNodeRef
import kadx.api.data.impl.KadxCodeComment
import kadx.api.data.impl.KadxNodeRef
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 注释风格：行注释 / 块注释 / 紧凑块注释 / JavaDoc 及其多行变体都应正确渲染。
 */
class TestCodeCommentStyle : IntegrationTest() {

	@Test
	fun test() {
		val comments = mutableListOf<ICodeComment>()
		addFldComment(comments, "aSingleLine", "Test line comment", CommentStyle.LINE)
		addFldComment(comments, "aMultiLine", "Test multi\nline comment", CommentStyle.LINE)

		addFldComment(comments, "block", "Test block comment", CommentStyle.BLOCK)
		addFldComment(comments, "blockMulti", "Test multi\nline block comment", CommentStyle.BLOCK)
		addFldComment(comments, "blockCondensed", "Test condensed block comment", CommentStyle.BLOCK_CONDENSED)
		addFldComment(comments, "blockCondensedMulti", "Test condensed multi\nline block comment", CommentStyle.BLOCK_CONDENSED)

		addFldComment(comments, "javaDoc", "Test javaDoc comment", CommentStyle.JAVADOC)
		addFldComment(comments, "javaDocMulti", "Test multi\nline javaDoc comment", CommentStyle.JAVADOC)
		addFldComment(comments, "javaDocCondensed", "Test condensed javaDoc comment", CommentStyle.JAVADOC_CONDENSED)
		addFldComment(comments, "javaDocCondensedMulti", "Test condensed multi\nline javaDoc comment", CommentStyle.JAVADOC_CONDENSED)
		getCodeData().setComments(comments)

		assertThat(getClassNode(TestCodeCommentStyleFixture.TestCls::class.java))
			.code()
			.containsOne("// Test line comment")
			.containsOne("/* Test condensed block comment */")
	}

	private fun addFldComment(comments: MutableList<ICodeComment>, fldName: String, comment: String, style: CommentStyle) {
		val clsName = "kadx.tests.integration.code.TestCodeCommentStyleFixture\$TestCls"
		val fldRef = KadxNodeRef(IJavaNodeRef.RefType.FIELD, clsName, "$fldName:I")
		comments.add(KadxCodeComment(fldRef, comment, style))
	}
}
