package kadx.plugins.kotlin.metadata.tests

import kadx.plugins.kotlin.smap.KotlinSmapOptions.Companion.CLASS_ALIAS_SOURCE_DBG_OPT
import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import kadx.tests.api.utils.assertj.KadxCodeAssertions
import org.junit.jupiter.api.Test

class TestSourceDebugExtension : SmaliTest() {

	@Test
	fun testRenameClass() {
		setupArgs {
			this[CLASS_ALIAS_SOURCE_DBG_OPT] = true
		}
		assertThatClass()
			.containsOne("androidx.compose.ui")
			.containsOne("public final class ActualKt")
			.countString(1, "reason: from SourceDebugExtension")
	}

	private fun setupArgs(builder: MutableMap<String, Boolean>.() -> Unit = {}) {
		val allOff = mutableMapOf(
			CLASS_ALIAS_SOURCE_DBG_OPT to false,
		)
		args.pluginOptions = allOff.apply(builder).mapValues {
			if (it.value) "yes" else "no"
		}
	}

	private fun assertThatClass(): KadxCodeAssertions = assertThat(getClassNodeFromSmaliFiles("deobf", "TestKotlinSourceDebugExtension", "C6"))
		.code()
}
