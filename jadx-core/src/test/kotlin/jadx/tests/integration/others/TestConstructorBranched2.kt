package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 分支化的 StringBuilder 构造：三个分支各自创建实例，反编译应保留三处 new。
 */
class TestConstructorBranched2 : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.countString(3, "new StringBuilder()")
	}
}
