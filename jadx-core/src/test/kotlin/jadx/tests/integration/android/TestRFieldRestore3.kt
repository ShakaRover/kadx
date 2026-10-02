package jadx.tests.integration.android

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * R 字段恢复（注解）：注解参数中的资源常量也应还原为 `R.id.xxx`。
 */
class TestRFieldRestore3 : IntegrationTest() {

	@Test
	fun test() {
		val map = HashMap<Int, String>()
		map[2131230730] = "id.Button"
		map[2137373737] = "id.MyId"
		setResMap(map)

		assertThat(getClassNode(TestRFieldRestore3Fixture.TestCls::class.java))
			.code()
			.containsOnlyOnce("@T(R.id.Button)")
			.containsOnlyOnce("@T(R.id.MyId)")
			.containsOnlyOnce("@F(R.id.Button)")
			.containsOnlyOnce("@M(bind = R.id.MyId)")
	}
}
