package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * finally 中的 closeQuietly 不应引入额外的临时 result 变量。
 */
class TestFinally2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestFinally2Fixture.TestCls::class.java))
			.code()
			.containsOne("decode(inputStream);")
			.containsOne("return new Result(400);")
			.doesNotContain("result =")
	}
}
