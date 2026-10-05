package kadx.tests.integration.arrays

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 数组字面量中的常量字段引用应保留为字段名而非替换成字面量。
 */
class TestArrayFillConstReplace : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestArrayFillConstReplaceFixture.TestCls::class.java))
			.code()
			.containsOne(" int CONST_INT = 65535;")
			.containsOne("return new int[]{127, 129, CONST_INT};")
	}
}
