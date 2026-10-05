package kadx.tests.integration.enums

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 枚举带自定义静态初始化块：应还原成枚举常量而非直接 `new` 构造。
 */
class TestEnumsWithCustomInit : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestEnumsWithCustomInitFixture.TestCls::class.java))
			.code()
			.containsOne("ONE(\"I\"),")
			.doesNotContain("new TestEnumsWithCustomInitFixture\$TestCls(")
	}
}
