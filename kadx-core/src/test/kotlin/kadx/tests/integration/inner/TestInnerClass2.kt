package kadx.tests.integration.inner

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 私有静态内部类 `TerminateTask`：实例化应保留具名类型，不出现匿名类痕迹。
 */
class TestInnerClass2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestInnerClass2Fixture.TestCls::class.java))
			.code()
			.contains("new Timer().schedule(new TerminateTask(), 1000L);")
			.doesNotContain("synthetic")
			.doesNotContain("this")
			.doesNotContain("null")
			.doesNotContain("AnonymousClass")
	}
}
