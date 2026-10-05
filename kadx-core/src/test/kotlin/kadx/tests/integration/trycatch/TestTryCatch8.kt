package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * synchronized 块中的自定义异常 catch 链应被正确还原。
 */
class TestTryCatch8 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTryCatch8Fixture.TestCls::class.java))
			.code()
			.containsOne("synchronized (this) {")
			.containsOne("throw new MyException();")
			.containsOne("} catch (MyException myExc) {")
			.containsOne("this.e = myExc;")
			.containsOne("} catch (Exception ex) {")
			.containsOne("this.e = new MyException(\"MyExc\", ex);")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		assertThat(getClassNode(TestTryCatch8Fixture.TestCls::class.java))
			.code()
			.containsOne("synchronized (this) {")
			.containsOne("throw new MyException();")
	}
}
