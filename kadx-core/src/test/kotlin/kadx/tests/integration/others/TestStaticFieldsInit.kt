package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 静态字段初始化：`null` 常量与静态块中的赋值应正确区分输出，
 * 被静态块赋值的常量不应被错误内联为 `null`。
 */
class TestStaticFieldsInit : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestStaticFieldsInitFixture.TestCls::class.java))
			.code()
			.doesNotContain("public static final String S2 = null;")
			.contains("public static final String S3 = null;")
	}
}
