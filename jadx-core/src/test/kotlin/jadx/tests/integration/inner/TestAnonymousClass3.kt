package jadx.tests.integration.inner

import jadx.NotYetImplemented
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.TestUtils.indent
import jadx.tests.api.utils.assertj.JadxAssertions
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 匿名类中对外部类字段的复合赋值（自增/自减/复合运算）的还原。
 */
class TestAnonymousClass3 : IntegrationTest() {

	@Test
	fun test() {
		disableCompilation()
		JadxAssertions.assertThat(getClassNode(TestAnonymousClass3Fixture.TestCls::class.java))
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
