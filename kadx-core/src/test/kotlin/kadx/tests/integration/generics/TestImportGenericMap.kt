package kadx.tests.integration.generics

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import kadx.tests.integration.generics.TestImportGenericMapFixture.SuperClass.NotToImport
import kadx.tests.integration.generics.TestImportGenericMapFixture.SuperClass.ToImport
import org.junit.jupiter.api.Test

/**
 * 泛型边界引用内部接口时：被引用的 `ToImport` 应生成 import，未引用的 `NotToImport` 不生成。
 */
class TestImportGenericMap : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestImportGenericMapFixture.SuperClass::class.java))
			.code()
			.contains("import " + ToImport::class.java.name.replace("\$ToImport", ".ToImport") + ';')
			.doesNotContain("import " + NotToImport::class.java.name.replace("NotToImport", ".NotToImport") + ';')
	}
}
