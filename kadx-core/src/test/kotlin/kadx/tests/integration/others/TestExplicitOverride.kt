package kadx.tests.integration.others

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 显式覆写：smali 中显式覆写的方法应保留 @Override 注解。
 */
class TestExplicitOverride : SmaliTest() {
	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.countString(1, "@Override")
	}
}
