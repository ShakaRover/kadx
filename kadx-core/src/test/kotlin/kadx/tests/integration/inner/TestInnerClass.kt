package kadx.tests.integration.inner

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 内部类继承 `Thread`：应省略隐式 `super()` 与合成的外部类引用字段。
 */
class TestInnerClass : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestInnerClassFixture.TestCls::class.java))
			.code()
			.contains("Inner {")
			.contains("Inner2 extends Thread {")
			.doesNotContain("super();")
			.doesNotContain("this$")
			.doesNotContain("/* synthetic */")
	}
}
