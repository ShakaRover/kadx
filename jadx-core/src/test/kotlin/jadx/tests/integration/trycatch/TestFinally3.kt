package jadx.tests.integration.trycatch

import jadx.NotYetImplemented
import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.TestUtils.indent
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * finally 提取：无调试信息时 finally 的提取尚不完善（已知未实现）。
 */
class TestFinally3 : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestFinally3Fixture.TestCls::class.java))
			.code()
			.containsOne("} finally {")
			.doesNotContain("close(null);")
			.containsOne("close(inputStream);")
	}

	@Test
	@NotYetImplemented("Finally extract failed")
	fun test2NoDebug() {
		noDebugInfo()
		assertThat(getClassNode(TestFinally3Fixture.TestCls::class.java))
			.code()
			.containsOne("} finally {")
			.containsOne(indent() + "close(")
	}

	@Test
	fun testSmali() {
		assertThat(getClassNodeFromSmali())
			.code()
			.doesNotContain("Type inference failed")
			.containsOne("} finally {")
	}
}
