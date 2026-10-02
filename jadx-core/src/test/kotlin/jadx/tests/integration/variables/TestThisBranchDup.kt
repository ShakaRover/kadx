package jadx.tests.integration.variables

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
