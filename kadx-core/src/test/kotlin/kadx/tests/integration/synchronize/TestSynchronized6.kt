package kadx.tests.integration.synchronize

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 同步块内的 `||` 短路表达式：Java 输入与 smali 输入都应还原出同步块。
 */
class TestSynchronized6 : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestSynchronized6Fixture.TestCls::class.java))
			.code()
			.containsOne("synchronized (this.lock) {")
			.containsOne("isA(obj) || isB(obj);") // TODO: "return isA(obj) || isB(obj);"
	}

	@Test
	fun testSmali() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("synchronized (this.lock) {")
		// TODO: .containsOne("return isA(obj) || isB(obj);");
	}
}
