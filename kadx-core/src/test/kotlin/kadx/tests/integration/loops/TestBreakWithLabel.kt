package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 带标签的 break（跳出双层循环）应还原出 `loop0:` 标签与 `break loop0;`。
 */
class TestBreakWithLabel : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestBreakWithLabelFixture.TestCls::class.java))
			.code()
			.containsOne("loop0:")
			.containsOne("break loop0;")
	}
}
