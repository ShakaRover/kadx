package kadx.tests.integration.java8

import kadx.tests.api.RaungTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 交叉类型（`TestCls<R> & Memoized`）上的方法引用应被还原，并支持 raung 输入。
 */
class TestLambdaInstance3 : RaungTest() {

	@Test
	fun test() {
		// some java versions failed to compile usage of interface with '$' in name
		addClsRename("kadx.tests.integration.java8.TestLambdaInstance3Fixture\$TestCls", "java8.TestCls")
		assertThat(getClassNode(TestLambdaInstance3Fixture.TestCls::class.java))
			.code()
			.doesNotContain("this::get")
			.containsOne("return (TestCls) lazyOf::get;")
		// TODO: type inference set type for 'lazyOf' to Memoized and cast incorrectly removed
		// .containsOne("Memoized)");
	}

	@Test
	fun testRaung() {
		disableCompilation()
		assertThat(getClassNodeFromRaung())
			.code()
			.doesNotContain("this::get")
			.containsOne(" lazyOf::get")
	}
}
