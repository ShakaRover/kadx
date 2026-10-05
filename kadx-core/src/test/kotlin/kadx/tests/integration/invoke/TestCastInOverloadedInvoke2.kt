package kadx.tests.integration.invoke

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 重载方法调用中的 null 参数：应保留 `(Parcelable)` 强制转换。
 */
class TestCastInOverloadedInvoke2 : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("new Intent().putExtra(\"param\", (Parcelable) null);")
	}
}
