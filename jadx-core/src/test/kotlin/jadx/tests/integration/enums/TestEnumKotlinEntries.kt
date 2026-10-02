package jadx.tests.integration.enums

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Kotlin 1.9+ 的枚举 `$ENTRIES` 模式：应还原枚举常量列表，
 * 并把 `$ENTRIES` 还原为 `EnumEntriesKt.enumEntries(values())`。
 */
class TestEnumKotlinEntries : SmaliTest() {

	@Test
	fun test() {
		disableCompilation() // kotlin.enums.EnumEntries not on test classpath
		assertThat(getClassNodeFromSmali())
			.code()
			.containsLines(1, "ALPHA,", "BETA,", "GAMMA;")
			.containsOne("EnumEntries \$ENTRIES = EnumEntriesKt.enumEntries(values());")
			.doesNotContain("\$VALUES")
			.doesNotContain("Failed to restore enum")
	}
}
