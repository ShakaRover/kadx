package jadx.tests.integration.inner

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 多层内部类（I0/I1）与匿名类混合：应还原出嵌套类声明。
 */
class TestInnerClass5 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestInnerClass5Fixture.TestCls::class.java))
			.code()
			.containsOne("public class I0 {")
			.containsOne("public class I1 {")
	}
}
