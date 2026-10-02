package jadx.tests.integration.conditions

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 构造器中三元表达式只出现在一个分支：`this(...)` 的参数三元不应被拆成注释。
 */
class TestTernaryOneBranchInConstructor : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestTernaryOneBranchInConstructorFixture.TestCls::class.java))
			.code()
			.containsOne("this(str == null ? 0 : i);")
			.doesNotContain("//")
			.doesNotContain("call moved to the top of the method")
	}

	@Test
	fun test2() {
		noDebugInfo()
		JadxAssertions.assertThat(getClassNode(TestTernaryOneBranchInConstructorFixture.TestCls2::class.java))
			.code()
			.containsOne("this(i == 1 ? str : \"\", i == 0 ? \"\" : str);")
			.doesNotContain("//")
	}
}
