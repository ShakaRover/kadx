package kadx.core.dex.info

import kadx.api.plugins.input.data.AccessFlags
import kadx.core.dex.info.AccessInfo.AFType
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

class AccessInfoTest {

	@Test
	fun changeVisibility() {
		val accessInfo = AccessInfo(AccessFlags.PROTECTED or AccessFlags.STATIC, AFType.METHOD)
		val result = accessInfo.changeVisibility(AccessFlags.PUBLIC)

		assertThat(result.isPublic()).isTrue()
		assertThat(result.isPrivate()).isFalse()
		assertThat(result.isProtected()).isFalse()

		assertThat(result.isStatic()).isTrue()
	}

	@Test
	fun changeVisibilityNoOp() {
		val accessInfo = AccessInfo(AccessFlags.PUBLIC, AFType.METHOD)
		val result = accessInfo.changeVisibility(AccessFlags.PUBLIC)
		assertThat(result).isSameAs(accessInfo)
	}
}
