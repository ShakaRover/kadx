package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 原始类型强制转换（实例初始化块内）：`float` 转 `long` 后与字段做位与运算，
 * 反编译不应崩溃。
 *
 * 来源：https://github.com/skylot/jadx/issues/1620
 */
class TestPrimitiveCasts2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestPrimitiveCasts2Fixture.TestCls::class.java))
			.code()
	}
}
