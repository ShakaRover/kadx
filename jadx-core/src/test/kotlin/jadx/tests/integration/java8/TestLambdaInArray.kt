package jadx.tests.integration.java8

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
