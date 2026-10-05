package kadx.core.plugins.versions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VerifyRequiredVersionTest {
	@Test
	fun test() {
		isCompatible("1.5.0, r2000", "1.5.1", true)
		isCompatible("1.5.1, r3000", "1.5.1", true)
		isCompatible("1.5.1, r3000", "1.6.0", true)
		isCompatible("1.5.1, r3000", "1.5.0", false)

		isCompatible("1.5.1, r3000", "r3001.417bb7a", true)
		isCompatible("1.5.1, r3000", "r4000", true)
		isCompatible("1.5.1, r3000", "r3000", true)
		isCompatible("1.5.1, r3000", "r2000", false)
	}

	companion object {
		private fun isCompatible(requiredVersion: String, kadxVersion: String, result: Boolean) {
			assertThat(VerifyRequiredVersion(kadxVersion).isCompatible(requiredVersion))
				.`as`(
					"Expect plugin with required version %s is%s compatible with kadx %s",
					requiredVersion,
					if (result) "" else " not",
					kadxVersion,
				)
				.isEqualTo(result)
		}
	}
}
