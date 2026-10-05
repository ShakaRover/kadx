package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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
