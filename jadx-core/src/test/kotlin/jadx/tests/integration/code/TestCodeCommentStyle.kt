package jadx.tests.integration.code

import jadx.api.data.CommentStyle
import jadx.api.data.ICodeComment
import jadx.api.data.IJavaNodeRef
import jadx.api.data.impl.JadxCodeComment
import jadx.api.data.impl.JadxNodeRef
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
		val clsName = "jadx.tests.integration.code.TestCodeCommentStyleFixture\$TestCls"
		val fldRef = JadxNodeRef(IJavaNodeRef.RefType.FIELD, clsName, "$fldName:I")
		comments.add(JadxCodeComment(fldRef, comment, style))
	}
}
