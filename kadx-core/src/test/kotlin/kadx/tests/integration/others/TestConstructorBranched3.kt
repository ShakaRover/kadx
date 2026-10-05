package kadx.tests.integration.others

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 分支化的构造返回值：两处分支都直接 `return new f(...)`，应保留两处。
 */
class TestConstructorBranched3 : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.countString(2, "return new f(")
	}
}
