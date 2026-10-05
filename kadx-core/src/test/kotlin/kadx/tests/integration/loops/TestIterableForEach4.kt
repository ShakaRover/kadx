package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 遍历 List 且带复合条件 break 的循环不应被还原为 while。
 */
class TestIterableForEach4 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestIterableForEach4Fixture.TestCls::class.java))
			.code()
			.doesNotContain("while (")
	}
}
