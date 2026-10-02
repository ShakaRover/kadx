package jadx.tests.integration.android

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * R 字段恢复：通过资源表把裸常量还原为 `R.id.Button` 访问。
 */
class TestRFieldRestore2 : IntegrationTest() {

	@Test
	fun test() {
		val map = HashMap<Int, String>()
		map[2131230730] = "id.Button"
		setResMap(map)

		JadxAssertions.assertThat(getClassNode(TestRFieldRestore2Fixture.TestCls::class.java))
			.code()
			.containsOne("R.id.Button;")
	}
}
