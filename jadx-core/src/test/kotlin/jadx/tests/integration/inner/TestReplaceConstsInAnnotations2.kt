package jadx.tests.integration.inner

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 注解数组元素中的常量引用：应保留为字段引用而非直接内联数值。
 */
class TestReplaceConstsInAnnotations2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestReplaceConstsInAnnotations2Fixture.TestCls::class.java))
			.code()
			// .containsOne("@A(C.INT_CONST)") // TODO: remove brackets for single element
			.containsOne("@A({C.INT_CONST}")
			.containsOne("@A({C.INT_CONST, C2.INT_CONST})")
			.containsOne("23412342")
			.containsOne("34563456")
	}
}
