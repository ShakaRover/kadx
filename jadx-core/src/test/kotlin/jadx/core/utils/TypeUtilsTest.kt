package jadx.core.utils

import jadx.api.JadxArgs
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.RootNode
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class TypeUtilsTest {
	private companion object {
		private val LOG = LoggerFactory.getLogger(TypeUtilsTest::class.java)
		private lateinit var root: RootNode

		@BeforeAll
		@JvmStatic
		fun init() {
			root = RootNode(JadxArgs())
			root.initClassPath()
		}
	}

	@Test
	fun testReplaceGenericsWithWildcards() {
		// check classpath graph
		val classGenerics = root.typeUtils.getClassGenerics(ArgType.`object`("java.util.ArrayList"))
		assertThat(classGenerics).hasSize(1)
		val genericInfo = classGenerics[0]
		assertThat(genericInfo.getObject()).isEqualTo("E")
		assertThat(genericInfo.getExtendTypes()).hasSize(0)

		// prepare input
		val instanceType = ArgType.generic("java.util.ArrayList", ArgType.OBJECT)
		LOG.debug("instanceType: {}", instanceType)

		val generic = ArgType.generic(
			"java.util.List",
			ArgType.wildcard(ArgType.genericType("E"), ArgType.WildcardBound.SUPER),
		)
		LOG.debug("generic: {}", generic)

		// replace
		val result = root.typeUtils.replaceClassGenerics(instanceType, generic)
		LOG.debug("result: {}", result)

		val expected = ArgType.generic(
			"java.util.List",
			ArgType.wildcard(ArgType.OBJECT, ArgType.WildcardBound.SUPER),
		)
		assertThat(result).isEqualTo(expected)
	}
}
