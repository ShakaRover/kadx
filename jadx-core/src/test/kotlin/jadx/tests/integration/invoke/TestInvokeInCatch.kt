package jadx.tests.integration.invoke

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * catch 块中的变参方法调用：不应插入多余 `return;`。
 */
class TestInvokeInCatch : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestInvokeInCatchFixture.TestCls::class.java))
			.code()
			.containsOne("try {")
			.containsOne("exc();")
			.doesNotContain("return;")
			.containsOne("} catch (IOException e) {")
			.containsOne("if (b == 1) {")
			.containsOne("log(TAG, \"Error: {}\", e.getMessage());")
	}
}
