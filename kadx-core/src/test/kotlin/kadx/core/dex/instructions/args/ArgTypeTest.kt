package kadx.core.dex.instructions.args

import kadx.core.dex.instructions.args.ArgType.WildcardBound.SUPER
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

class ArgTypeTest {

	@Test
	fun testEqualsOfGenericTypes() {
		val first = ArgType.generic("java.lang.List", ArgType.STRING)
		val second = ArgType.generic("Ljava/lang/List;", ArgType.STRING)

		assertThat(first).isEqualTo(second)
	}

	@Test
	fun testContainsGenericType() {
		val wildcard = ArgType.wildcard(ArgType.genericType("T"), SUPER)
		assertThat(wildcard.containsTypeVariable()).isTrue()

		val type = ArgType.generic("java.lang.List", wildcard)
		assertThat(type.containsTypeVariable()).isTrue()
	}

	@Test
	fun testInnerGeneric() {
		val genericTypes = listOf(ArgType.genericType("K"), ArgType.genericType("V"))
		val base = ArgType.generic("java.util.Map", genericTypes)

		val genericInner = ArgType.outerGeneric(base, ArgType.generic("Entry", genericTypes))
		assertThat(genericInner.toString()).isEqualTo("java.util.Map<K, V>\$Entry<K, V>")
		assertThat(genericInner.containsTypeVariable()).isTrue()

		val genericInner2 = ArgType.outerGeneric(base, ArgType.`object`("Entry"))
		assertThat(genericInner2.toString()).isEqualTo("java.util.Map<K, V>\$Entry")
		assertThat(genericInner2.containsTypeVariable()).isTrue()
	}
}
