package jadx.tests.integration.generics

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Parcelable 泛型方法覆写：`createFromParcel`/`newArray` 应保留具体类型与 `@Override`。
 */
class TestMethodOverride : SmaliTest() {

	@Test
	fun test() {
		disableCompilation()

		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("String createFromParcel(Parcel parcel) {")
			.containsOne("String[] newArray(int i) {")
			.countString(2, "@Override")
	}
}
