package jadx.tests.integration.enums

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
