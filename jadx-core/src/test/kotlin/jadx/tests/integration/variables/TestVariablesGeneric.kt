package jadx.tests.integration.variables

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 泛型变量：类型参数 `T` 与通配符 `? super T` 应正确保留，且不产生 `iVar2` 之类的重复变量。
 *
 * 对应 smali 的等价 Java：
 * ```
 * public static <T> j a(i<? super T> iVar, c<T> cVar) {
 * 	if (iVar == null) {
 * 		throw new IllegalArgumentException("subscriber can not be null");
 * 	}
 * 	if (cVar.a == null) {
 * 		throw new IllegalStateException("onSubscribe function can not be null.");
 * 	}
 * 	...
 * }
 * ```
 */
class TestVariablesGeneric : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.doesNotContain("iVar2")
			.containsOne("public static <T> j a(i<? super T> iVar, c<T> cVar) throws OnErrorFailedException {")
			.containsOne("if (iVar == null) {")
			.countString(2, "} catch (Throwable th")
	}
}
