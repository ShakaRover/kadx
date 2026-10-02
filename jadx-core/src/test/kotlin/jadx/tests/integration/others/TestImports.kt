package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * import 处理：同名内部类 Character 与 java.lang.Character 的 import 应正确区分。
 */
@Suppress("DataFlowIssue")
class TestImports : IntegrationTest() {

	@Test
	fun test1() {
		noDebugInfo()
		assertThat(getClassNode(TestImportsFixture.TestCls1::class.java))
			.code()
			.doesNotContain("import java.lang.Character;") // import not needed
	}

	@Test
	fun test2() {
		noDebugInfo()
		assertThat(getClassNode(TestImportsFixture.TestCls2::class.java))
			.code()
	}
}
