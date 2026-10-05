package kadx.tests.integration.inner

import kadx.NotYetImplemented
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.TestUtils.Companion.indent
import kadx.tests.api.utils.assertj.KadxAssertions
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 匿名类中对外部类字段的复合赋值（自增/自减/复合运算）的还原。
 */
class TestAnonymousClass3 : IntegrationTest() {

	@Test
	fun test() {
		disableCompilation()
		KadxAssertions.assertThat(getClassNode(TestAnonymousClass3Fixture.TestCls::class.java))
			.code()
			.contains(indent(4) + "public void run() {")
			.contains(indent(3) + "}.start();")
			.doesNotContain("AnonymousClass_")
	}

	@Test
	@NotYetImplemented
	fun test2() {
		disableCompilation()
		assertThat(getClassNode(TestAnonymousClass3Fixture.TestCls::class.java))
			.code()
			.doesNotContain("synthetic")
			.contains("a = f--;")
	}
}
