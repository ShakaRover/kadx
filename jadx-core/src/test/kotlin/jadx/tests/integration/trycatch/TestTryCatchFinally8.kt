package jadx.tests.integration.trycatch

import jadx.NotYetImplemented
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 合并的 catch 块（多个 catch 共享代码）目前尚未正确还原（已知未实现）。
 */
class TestTryCatchFinally8 : IntegrationTest() {

	@Test
	@NotYetImplemented("Fix merged catch blocks (shared code between catches)")
	fun test() {
		assertThat(getClassNode(TestTryCatchFinally8Fixture.TestCls::class.java))
			.code()
			.containsOne("FileOutputStream output = null;")
			.countString(2, "try {")
			.countString(2, "} catch (IOException e")
			.containsOne("} finally {")
			.containsOne("file.delete();")
	}
}
