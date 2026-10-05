package kadx.tests.integration.android

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 资源常量替换（注解值）：注解参数中的 `android.R.string` 常量应替换为 `R.string.xxx`。
 */
class TestResConstReplace3 : IntegrationTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNode(TestResConstReplace3Fixture.TestCls::class.java))
			.code()
			.containsOne("import android.R;")
			.countString(2, "@TestResConstReplace3Fixture.UsesAndroidResource(R.string.ok)")
	}
}
