package kadx.tests.integration.others

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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
