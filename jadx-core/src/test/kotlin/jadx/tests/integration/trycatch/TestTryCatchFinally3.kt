package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 遍历 passes 时捕获异常并在 finally 中卸载类：catch 与 finally 都应保留。
 */
class TestTryCatchFinally3 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestTryCatchFinally3Fixture.TestCls::class.java))
			.code()
			.containsOne("for (IDexTreeVisitor visitor : passes) {")
			.containsOne("} catch (Exception e) {")
			.containsOne("LOG.error(\"Class process exception: {}\", cls, e);")
			.containsOne("} finally {")
			.containsOne("cls.unload();")
	}
}
