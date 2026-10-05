package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 构造器中的空 catch：try 与其内的 if 判断都应保留。
 */
class TestTryWithEmptyCatch : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestTryWithEmptyCatchFixture.TestCls::class.java))
			.code()
			.containsOne("try {")
			.containsOne("if (")
	}
}
