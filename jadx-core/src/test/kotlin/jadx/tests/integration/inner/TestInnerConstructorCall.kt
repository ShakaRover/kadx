package jadx.tests.integration.inner

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 内部类的实例化调用：`a.new AA()` 语法应正确还原。
 */
class TestInnerConstructorCall : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestInnerConstructorCallFixture.TestCls::class.java))
			.code()
			.containsOne("A.AA aa = a.new AA();")
	}
}
