package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import org.junit.jupiter.api.Test

/**
 * 字段访问重排：字段读写顺序应保持正确（getClassNode 会自动执行 check()）。
 */
class TestFieldAccessReorder : IntegrationTest() {
	@Test
	fun test() {
		noDebugInfo()
		getClassNode(TestFieldAccessReorderFixture.TestCls::class.java)
		// auto check should pass
	}
}
