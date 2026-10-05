package kadx.tests.functional

import kadx.api.KadxArgs
import kadx.core.clsp.ClspGraph
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.nodes.RootNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 类路径（clsp）加载后，类型继承与强制转换判定的正确性。
 */
class KadxClasspathTest {

	private lateinit var root: RootNode
	private lateinit var clsp: ClspGraph

	@BeforeEach
	fun initClsp() {
		root = RootNode(KadxArgs())
		root.loadClasses(emptyList())
		root.initClassPath()
		clsp = checkNotNull(root.getClsp())
	}

	@Test
	fun test() {
		val objExc = ArgType.`object`(JAVA_LANG_EXCEPTION)
		val objThr = ArgType.`object`(JAVA_LANG_THROWABLE)

		assertThat(clsp.isImplements(JAVA_LANG_EXCEPTION, JAVA_LANG_THROWABLE)).isTrue()
		assertThat(clsp.isImplements(JAVA_LANG_THROWABLE, JAVA_LANG_EXCEPTION)).isFalse()

		assertThat(ArgType.isCastNeeded(root, objExc, objThr)).isFalse()
		assertThat(ArgType.isCastNeeded(root, objThr, objExc)).isTrue()

		assertThat(ArgType.isCastNeeded(root, ArgType.OBJECT, ArgType.STRING)).isTrue()
	}

	companion object {
		private const val JAVA_LANG_EXCEPTION = "java.lang.Exception"
		private const val JAVA_LANG_THROWABLE = "java.lang.Throwable"
	}
}
