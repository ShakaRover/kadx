package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 静态方法调用：静态初始化块中调用私有静态方法，两者都应保留。
 */
class TestStaticMethod : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestStaticMethodFixture.TestCls::class.java))
			.code()
			.contains("static {")
			.contains("private static void f() {")
	}
}
