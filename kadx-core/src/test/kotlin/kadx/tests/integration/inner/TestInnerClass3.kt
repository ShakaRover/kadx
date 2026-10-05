package kadx.tests.integration.inner

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 内部类调用外部类的私有方法：应直接以 `setC` 形式调用，不产生 synthetic 桥接或 `x0` 参数。
 */
class TestInnerClass3 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestInnerClass3Fixture.TestCls::class.java))
			.code()
			.doesNotContain("synthetic")
			.doesNotContain("access$")
			.doesNotContain("x0")
			.contains("setC(\"c\");")
	}
}
