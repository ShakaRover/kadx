package jadx.tests.integration.android

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 资源常量替换（switch 分支）：`android.R.attr` 常量应替换为 `R.attr.xxx` 并加 import。
 */
class TestResConstReplace2 : IntegrationTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNode(TestResConstReplace2Fixture.TestCls::class.java))
			.code()
			.containsOne("import android.R;")
			.containsOne("case R.attr.minWidth:")
	}
}
