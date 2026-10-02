package jadx.tests.integration.generics

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import jadx.tests.integration.generics.TestImportGenericMapFixture.SuperClass.NotToImport
import jadx.tests.integration.generics.TestImportGenericMapFixture.SuperClass.ToImport
import org.junit.jupiter.api.Test

/**
 * 泛型边界引用内部接口时：被引用的 `ToImport` 应生成 import，未引用的 `NotToImport` 不生成。
 */
class TestImportGenericMap : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestImportGenericMapFixture.SuperClass::class.java))
			.code()
			.contains("import " + ToImport::class.java.name.replace("\$ToImport", ".ToImport") + ';')
			.doesNotContain("import " + NotToImport::class.java.name.replace("NotToImport", ".NotToImport") + ';')
	}
}
