package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * finally 中的 closeQuietly 不应引入额外的临时 result 变量。
 */
class TestFinally2 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestFinally2Fixture.TestCls::class.java))
			.code()
			.containsOne("decode(inputStream);")
			.containsOne("return new Result(400);")
			.doesNotContain("result =")
	}
}
