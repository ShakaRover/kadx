package kadx.tests.integration.inner

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 匿名类捕获外部实例：应还原为 `new A(this, ...) { ... }` 形式，且不残留 synthetic 成员。
 */
class TestAnonymousClass10 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestAnonymousClass10Fixture.TestCls::class.java))
			.code()
			.containsOne("return new A(this, a2, a2 + 3, 4, 5, random.nextDouble()) {")
			.doesNotContain("synthetic")
	}
}
