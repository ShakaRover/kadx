package kadx.tests.integration.rename

import kadx.api.data.ICodeRename
import kadx.api.data.IJavaNodeRef.RefType
import kadx.api.data.impl.KadxCodeData
import kadx.api.data.impl.KadxCodeRename
import kadx.api.data.impl.KadxNodeRef
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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

		val codeData = KadxCodeData()
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
				"/* KADX INFO: renamed from: c */",
				"@SerializedName(\"title\")",
				"private String title;",
				"",
			)
	}

	private fun fieldRename(baseClsId: String, shortId: String, id: String): KadxCodeRename = KadxCodeRename(KadxNodeRef(RefType.FIELD, baseClsId, shortId), id)
}
