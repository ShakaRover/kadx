package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * try 内 synchronized 与 wait 调用应被正确还原。
 */
class TestTryCatch2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestTryCatch2Fixture.TestCls::class.java))
			.code()
			.contains("try {")
			.contains("synchronized (OBJ) {")
			.contains("OBJ.wait(5L);")
			.contains("return true;")
			.contains("} catch (InterruptedException e) {")
			.contains("return false;")
	}
}
