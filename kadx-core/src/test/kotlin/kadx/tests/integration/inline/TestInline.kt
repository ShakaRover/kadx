package kadx.tests.integration.inline

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * `new TestCls().testRun()` 的实例化不应被内联掉，构造调用必须保留。
 */
class TestInline : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestInlineFixture.TestCls::class.java))
			.code()
			.contains("System.out.println(\"Test: \" + new TestInlineFixture\$TestCls().testRun());")
	}
}
