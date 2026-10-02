package jadx.tests.integration.others

import jadx.tests.api.RaungTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 类签名中的接口实现：泛型自引用 `Comparable<A<T>>` 应被正确保留。
 */
class TestClassImplementsSignature : RaungTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestClassImplementsSignatureFixture.TestCls::class.java))
			.code()
			.containsOne("public static abstract class A<T> implements Comparable<A<T>> {")
	}

	@Test
	fun testRaung() {
		allowWarnInCode()
		assertThat(getClassNodeFromRaung())
			.code()
			.containsOne("public class TestClassImplementsSignature<T> {")
			.containsOne("Unexpected interfaces in signature")
	}
}
