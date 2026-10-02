package jadx.tests.integration.arrays

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
