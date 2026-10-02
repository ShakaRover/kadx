package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * try 内 synchronized 与 wait 调用应被正确还原。
 */
class TestTryCatch2 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestTryCatch2Fixture.TestCls::class.java))
			.code()
			.contains("try {")
			.contains("synchronized (OBJ) {")
			.contains("OBJ.wait(5L);")
			.contains("return true;")
			.contains("} catch (InterruptedException e) {")
			.contains("return false;")
	}
}
