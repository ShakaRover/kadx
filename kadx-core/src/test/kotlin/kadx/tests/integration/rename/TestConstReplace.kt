package kadx.tests.integration.rename

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 常量替换：静态常量引用应还原为常量名（首次反编译与重新加载代码都一致）。
 */
class TestConstReplace : IntegrationTest() {

	@Test
	fun test() {
		val cls = getClassNode(TestConstReplaceFixture.TestCls::class.java)
		assertThat(cls)
			.code()
			.containsOnlyOnce("return CONST;")

		assertThat(cls)
			.reloadCode(this)
			.containsOnlyOnce("return CONST;")
	}
}
