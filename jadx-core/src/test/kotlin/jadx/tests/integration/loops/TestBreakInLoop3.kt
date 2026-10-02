package jadx.tests.integration.loops

import jadx.NotYetImplemented
import jadx.tests.api.IntegrationTest

/**
 * 循环内 try/catch 且 catch 中 continue 的写文件重试逻辑；该用例尚未实现（已知失败）。
 */
class TestBreakInLoop3 : IntegrationTest() {

	// @Test
	@NotYetImplemented
	fun test43() {
		getClassNode(TestBreakInLoop3Fixture.TestCls::class.java)
	}
}
