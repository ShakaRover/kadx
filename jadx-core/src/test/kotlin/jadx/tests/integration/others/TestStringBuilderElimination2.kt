package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 测试 [jadx.core.dex.visitors.SimplifyVisitor] 中的 StringBuilder 简化逻辑。
 *
 * @author Jan Peter Stotz
 */
class TestStringBuilderElimination2 : IntegrationTest() {

	@Test
	fun test1() {
		JadxAssertions.assertThat(getClassNode(TestStringBuilderElimination2Fixture.TestCls1::class.java))
			.code()
			.contains("return \"[init]a1c201.02.0true\";")
	}

	@Test
	fun test2() {
		JadxAssertions.assertThat(getClassNode(TestStringBuilderElimination2Fixture.TestCls2::class.java))
			.code()
			.contains("return \"[init]a1c121.02.0true\";")
	}

	@Test
	fun test3() {
		JadxAssertions.assertThat(getClassNode(TestStringBuilderElimination2Fixture.TestClsStringUtilsReverse::class.java))
			.code()
			.contains("return new StringBuilder(str).reverse().toString();")
	}

	@Test
	fun testChainWithDelete() {
		JadxAssertions.assertThat(getClassNode(TestStringBuilderElimination2Fixture.TestClsChainWithDelete::class.java))
			.code()
			.contains("return new StringBuilder(\"[init]\").append(\"a1\").delete(1, 2).toString();")
	}
}
