package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 静态字段初始化顺序：常量表达式依赖前面的静态字段时应正确内联。
 */
class TestFieldInitOrder2 : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestFieldInitOrder2Fixture.TestCls::class.java))
			.code()
			.containsOne("private static final String VALUE = ZPREFIX + \"VALUE\";")
	}

	@Test
	fun testSmali() {
		assertThat(getClassNodeFromSmali())
			.runDecompiledAutoCheck(this)
			.code()
			.containsOne("private static final String VALUE = ZPREFIX + \"VALUE\";")
	}
}
