package kadx.tests.integration.generics

import kadx.NotYetImplemented
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 外部类泛型实例化内部类（`a.new B<Exception>()`）的还原（已知缺陷）。
 */
class TestOuterGeneric : IntegrationTest() {

	@NotYetImplemented("Instance constructor for inner classes")
	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestOuterGenericFixture.TestCls::class.java))
			.code()
			.containsOne("A<String> a = new A<>();")
			.containsOne("A<String>.B<Exception> b = a.new B<Exception>();")
			.containsOne("A<String>.C c = a.new C();")
			.containsOne("use(new A<Set<String>>().new C());")
			.containsOne("D.E e = d.new E();")
	}
}
