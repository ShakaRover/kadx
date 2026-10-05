package kadx.tests.integration.variables

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * smali 测试：`this(...)` 构造器调用类型应被正确识别，
 * 三元表达式内联后 `(i & ` 应出现 6 次。
 */
class TestThisBranchDup : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("this(") // 构造器类型识别正确
			.countString(6, "(i & ") // 三元表达式被内联
	}
}
