package jadx.tests.integration.rename

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 泛型字段重命名：修改字段别名后，重新反编译应使用新名字且保留泛型签名。
 */
class TestFieldWithGenericRename : IntegrationTest() {

	@Test
	fun test() {
		val cls = getClassNode(TestFieldWithGenericRenameFixture.TestCls::class.java)
		assertThat(cls)
			.code()
			.containsOnlyOnce("List<String> list;")

		checkNotNull(cls.searchFieldByName("list")).getFieldInfo().alias = "listFieldRenamed"

		assertThat(cls)
			.reloadCode(this)
			.containsOnlyOnce("List<String> listFieldRenamed;")
	}
}
