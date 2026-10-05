package kadx.tests.integration.android

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 资源常量替换：`android.R.attr.minWidth` 常量应替换为 `R.attr.minWidth` 并加 import。
 */
class TestResConstReplace : IntegrationTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNode(TestResConstReplaceFixture.TestCls::class.java))
			.code()
			.containsOne("import android.R;")
			.containsOne("return R.attr.minWidth;")
	}
}
