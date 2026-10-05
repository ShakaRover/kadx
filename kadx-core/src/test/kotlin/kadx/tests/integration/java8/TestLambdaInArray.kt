package kadx.tests.integration.java8

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 数组中的方法引用（`this::call1`）应被正确还原。
 */
class TestLambdaInArray : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestLambdaInArrayFixture.TestCls::class.java))
			.code()
			.containsOne("return Arrays.asList(this::call1, this::call2);")
	}
}
