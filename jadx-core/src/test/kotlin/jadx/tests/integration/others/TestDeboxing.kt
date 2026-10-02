package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 基础类型装箱：各种基础类型作为 Object 返回时应还原为字面量而不保留装箱调用。
 */
class TestDeboxing : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		JadxAssertions.assertThat(getClassNode(TestDeboxingFixture.TestCls::class.java))
			.code()
			.containsOne("return 1;")
			.containsOne("return true;")
			.containsOne("return (byte) 2;")
			.containsOne("return (short) 3;")
			.containsOne("return 'c';")
			.containsOne("return 4L;")
			.countString(2, "use(true);")
	}
}
