package kadx.tests.integration.android

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * R 字段访问：`R.id.BUTTON_01` 的字段访问应原样保留。
 */
class TestRFieldAccess : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestRFieldAccessFixture::class.java))
			.code()
			.countString(1, "return R.id.BUTTON_01;")
	}
}
