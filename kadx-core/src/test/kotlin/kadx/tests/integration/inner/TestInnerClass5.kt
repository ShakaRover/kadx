package kadx.tests.integration.inner

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 多层内部类（I0/I1）与匿名类混合：应还原出嵌套类声明。
 */
class TestInnerClass5 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestInnerClass5Fixture.TestCls::class.java))
			.code()
			.containsOne("public class I0 {")
			.containsOne("public class I1 {")
	}
}
