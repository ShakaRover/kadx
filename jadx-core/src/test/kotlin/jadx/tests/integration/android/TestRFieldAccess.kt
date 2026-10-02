package jadx.tests.integration.android

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
