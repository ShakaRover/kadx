package jadx.tests.integration.invoke

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 内部类覆写方法中的 `super.a()` 调用：父类与子类代码都应正确还原。
 */
class TestSuperInvoke : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestSuperInvokeFixture::class.java))
			.code()
			.countString(1, "return super.a() + 2;")
	}
}
