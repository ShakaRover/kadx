package kadx.tests.integration.inner

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 匿名类捕获多个布尔局部变量，Java 源码与 smali 两种输入都应正确还原。
 */
class TestAnonymousClass19 : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestAnonymousClass19Fixture.TestCls::class.java))
			.code()
			.containsOne("System.out.println(a + \" && \" + b + \" = \" + c);")
	}

	@Test
	fun testSmali() {
		assertThat(getClassNodeFromSmaliFiles("ATestCls"))
			.code()
			.containsOne("System.out.println(a + \" && \" + b + \" = \" + c);")
	}
}
