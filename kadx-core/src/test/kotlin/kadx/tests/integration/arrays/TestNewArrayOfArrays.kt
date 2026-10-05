package kadx.tests.integration.arrays

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * `anewarray` / `multianewarray` 生成的多维数组声明应保留维度。
 */
class TestNewArrayOfArrays : IntegrationTest() {

	@Test
	fun test() {
		useJavaInput()
		assertThat(getClassNode(TestNewArrayOfArraysFixture.TestCls::class.java))
			.code()
			.containsOne("new long[n][]")
			.containsOne("new String[n][]")
			.containsOne("new int[n][][]")
			.containsOne("new int[a][b]")
	}
}
