package jadx.tests.integration.inline

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * `new TestCls().testRun()` 的实例化不应被内联掉，构造调用必须保留。
 */
class TestInline : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestInlineFixture.TestCls::class.java))
			.code()
			.contains("System.out.println(\"Test: \" + new TestInlineFixture\$TestCls().testRun());")
	}
}
