package jadx.tests.integration.rename

import jadx.api.data.ICodeRename
import jadx.api.data.IJavaNodeRef.RefType
import jadx.api.data.impl.JadxCodeData
import jadx.api.data.impl.JadxCodeRename
import jadx.api.data.impl.JadxNodeRef
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 字段重命名格式：`@SerializedName` 注解应保留，重命名字段带 `renamed from` 注释。
 */
class TestFieldRenameFormat : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()

		val baseClsId = TestFieldRenameFormatFixture.TestCls::class.java.name
		val renames: List<ICodeRename> = listOf(
			fieldRename(baseClsId, "b:I", "id"),
			fieldRename(baseClsId, "c:Ljava/lang/String;", "title"),
			fieldRename(baseClsId, "e:Ljava/util/List;", "authors"),
		)

		val codeData = JadxCodeData()
		codeData.setRenames(renames)
		getArgs().codeData = codeData
		getArgs().isDeobfuscationOn = false

		assertThat(getClassNode(TestFieldRenameFormatFixture.TestCls::class.java))
			.code()
			.containsOne("private int id;")
			.containsOne("private List<String> authors;")
			.containsLines(
				1,
				"",
				"/* JADX INFO: renamed from: c */",
				"@SerializedName(\"title\")",
				"private String title;",
				"",
			)
	}

	private fun fieldRename(baseClsId: String, shortId: String, id: String): JadxCodeRename = JadxCodeRename(JadxNodeRef(RefType.FIELD, baseClsId, shortId), id)
}
