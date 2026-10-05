package kadx.tests.integration.types

import kadx.NotYetImplemented
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 嵌套泛型 `Map<Set<?>, List<?>>` 的取值与回传：泛型方法签名与 `(List<T>)` 强转应保留。
 */
class TestTypeResolver13 : IntegrationTest() {

	@NotYetImplemented("additional cast for generic types")
	@Test
	fun test() {
		assertThat(getClassNode(TestTypeResolver13Fixture.TestCls::class.java))
			.code()
			.containsOne("public <T> List<T> test(Set<T> type) {")
			.containsOne("return (List<T>) obj;")
	}
}
