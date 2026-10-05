package kadx.tests.integration.inline

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 构造器委托（`this(...)`/`super(...)`）不应被内联，且内部类 `A` 的继承声明应完整。
 */
class TestInline3 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestInline3Fixture.TestCls::class.java))
			.code()
			.contains("this(b1, b2, 0, 0, 0);")
			.contains("super(a, a);")
			.doesNotContain("super(a, a).this\$0")
			.contains("public class A extends TestInline3Fixture\$TestCls {")
	}
}
