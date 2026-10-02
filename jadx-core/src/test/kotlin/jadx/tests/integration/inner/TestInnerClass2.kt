package jadx.tests.integration.inner

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 私有静态内部类 `TerminateTask`：实例化应保留具名类型，不出现匿名类痕迹。
 */
class TestInnerClass2 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestInnerClass2Fixture.TestCls::class.java))
			.code()
			.contains("new Timer().schedule(new TerminateTask(), 1000L);")
			.doesNotContain("synthetic")
			.doesNotContain("this")
			.doesNotContain("null")
			.doesNotContain("AnonymousClass")
	}
}
