package kadx.tests.integration.variables

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 同步块中初始化的集合在循环里使用：`synchronized` 块与随后的循环应保持原顺序。
 */
class TestVariablesUsageWithLoops : IntegrationTest() {

	@Test
	fun testEnhancedFor() {
		assertThat(getClassNode(TestVariablesUsageWithLoopsFixture.TestEnhancedFor::class.java))
			.code()
			.containsLine(2, "synchronized (this) {")
			.containsLine(3, "list = new ArrayList<>")
	}

	@Test
	fun testForLoop() {
		assertThat(getClassNode(TestVariablesUsageWithLoopsFixture.TestEnhancedFor::class.java))
			.code()
			.containsLine(2, "synchronized (this) {")
			.containsLine(3, "list = new ArrayList<>")
	}
}
