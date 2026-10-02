package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 构造器中的空 catch：try 与其内的 if 判断都应保留。
 */
class TestTryWithEmptyCatch : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestTryWithEmptyCatchFixture.TestCls::class.java))
			.code()
			.containsOne("try {")
			.containsOne("if (")
	}
}
