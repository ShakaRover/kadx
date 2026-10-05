package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 常量替换：默认将静态常量引用还原为常量名，关闭替换后应内联为字面量。
 */
class TestConstReplace : IntegrationTest() {

	@Test
	fun test() {
		val cls = getClassNode(TestConstReplaceFixture.TestCls::class.java)
		val testMth = checkNotNull(cls.searchMethodByShortName("test"))
		assertThat(testMth)
			.code()
			.print()
			.containsOne("return CONST_VALUE;")

		val constField = cls.searchFieldByName("CONST_VALUE")
		assertThat(constField).isNotNull()
		assertThat(checkNotNull(constField).useIn).containsExactly(testMth)
	}

	@Test
	fun testWithoutReplace() {
		getArgs().isReplaceConsts = false
		val cls = getClassNode(TestConstReplaceFixture.TestCls::class.java)
		assertThat(cls).code().containsOne("return \"string\";")

		val constField = cls.searchFieldByName("CONST_VALUE")
		assertThat(constField).isNotNull()
		assertThat(checkNotNull(constField).useIn).isEmpty()
	}
}
