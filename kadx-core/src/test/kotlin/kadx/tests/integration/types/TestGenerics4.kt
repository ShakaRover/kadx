package kadx.tests.integration.types

import kadx.NotYetImplemented
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 泛型重载选择：`overload(IList<? super T>)` 与 `overload(T)` 之间的调用需要保留通配符强转。
 */
class TestGenerics4 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestGenerics4Fixture.TestCls::class.java))
			.code()
			.containsOne("public static class ObjIList implements IList<Object> {")
			.containsOne("Inner<Object> inner = new Inner<>();")
			.containsOne("inner.overload((IList<? super Object>) new ObjIList());")
	}

	@NotYetImplemented
	@Test
	fun testOmitCast() {
		assertThat(getClassNode(TestGenerics4Fixture.TestCls::class.java))
			.code()
			.containsOne("inner.overload(new ObjIList());")
	}
}
