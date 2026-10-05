package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * finally 中依赖局部布尔变量的清理逻辑：应还原为 if (!success) 而非常量条件。
 */
class TestFinallyExtract : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestFinallyExtractFixture.TestCls::class.java))
			.code()
			.containsOne("} finally {")
			.doesNotContain("if (0 == 0) {")
			.containsOne("boolean success = false;")
			.containsOne("try {")
			.containsOne("success = true;")
			.containsOne("return value;")
			.containsOne("if (!success) {")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		KadxAssertions.assertThat(getClassNode(TestFinallyExtractFixture.TestCls::class.java))
			.code()
			.containsOne("this.result++;")
			.containsOne("} catch (Throwable th) {")
			.containsOne("this.result -= 2;")
			.containsOne("throw th;")

		// java compiler optimization: 'success' variable completely removed and no code duplication:
		// @formatter:off
		/*
		    public String test() {
		        try {
		            String call = call();
		            this.result++;
		            return call;
		        } catch (Throwable th) {
		            this.result -= 2;
		            throw th;
		        }
		    }
		 */
		// @formatter:on
	}
}
