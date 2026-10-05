package kadx.tests.integration.inner

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 匿名类捕获外部类的 `final` 字段：应直接以 `this.d` 形式访问，不产生 synthetic 成员。
 */
class TestAnonymousClass8 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestAnonymousClass8Fixture.TestCls::class.java))
			.code()
			.containsOne("public Runnable test() {")
			.containsOne("return new Runnable() {")
			.containsOne("public void run() {")
			.containsOne("this.d);")
			.doesNotContain("synthetic")
	}
}
