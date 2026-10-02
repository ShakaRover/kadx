package jadx.tests.integration.others

import jadx.tests.api.RaungTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * SWAP 指令：Java 输入与 raung 输入都应能正确反编译。
 */
class TestJavaSwap : RaungTest() {

	@Test
	fun testJava() {
		useJavaInput()
		assertThat(getClassNode(TestJavaSwapFixture.TestCls::class.java))
			.code()
	}

	@Test
	fun test() {
		useJavaInput()
		assertThat(getClassNodeFromRaung())
			.code()
	}
}
