package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
