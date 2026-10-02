package jadx.tests.integration.inner

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 注解参数中的常量引用：`f = C.FLOAT_CONST` 应保留字段引用。
 */
class TestReplaceConstsInAnnotations : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestReplaceConstsInAnnotationsFixture.TestCls::class.java))
			.code()
			.containsOnlyOnce("f = C.FLOAT_CONST")
	}
}
