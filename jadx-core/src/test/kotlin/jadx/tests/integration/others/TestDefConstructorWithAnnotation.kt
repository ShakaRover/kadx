package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 带注解的默认构造器不应被移除：构造器上的 @AnnotationTest 必须保留。
 */
class TestDefConstructorWithAnnotation : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestDefConstructorWithAnnotationFixture.TestCls::class.java))
			.code()
			.containsOne("@AnnotationTest")
	}
}
