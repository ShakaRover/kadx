package jadx.tests.integration.rename

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
