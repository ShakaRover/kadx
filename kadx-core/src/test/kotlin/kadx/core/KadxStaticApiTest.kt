package kadx.core

import kadx.api.KadxArgs
import kadx.core.dex.visitors.IDexTreeVisitor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

/**
 * 回归测试：锁定 `Kadx` 对**外部 Java 插件**暴露的 JVM 静态表面。
 *
 * 背景（OSS 反编译实测发现）：`Kadx` 是插件可见的工具类，随 kadx 分发的
 * `example-plugin` 会以**静态方式**调用 `kadx.core.Kadx.getVersion()`。
 * 在 Kotlin 化 / 现代化清理 `@JvmStatic` 时若误删，运行时会抛
 * `IncompatibleClassChangeError: Expected static method 'kadx.core.Kadx.getVersion()'`，
 * 导致插件初始化失败、整个反编译流程中断。
 *
 * 这里用反射把这些成员的静态性固定下来，防止再次回归。
 */
class KadxStaticApiTest {

	@Test
	fun `plugin facing methods are static on JVM`() {
		assertStaticMethod("getVersion")
		assertStaticMethod("getPassesList", KadxArgs::class.java)
		assertStaticMethod("getRegionsModePasses", KadxArgs::class.java)
		assertStaticMethod("getSimpleModePasses", KadxArgs::class.java)
		assertStaticMethod("isDevVersion")
	}

	@Test
	fun `plugin facing properties expose static getters`() {
		assertStaticMethod("getPreDecompilePassesList")
		assertStaticMethod("getFallbackPassesList")
	}

	@Test
	fun `pass list methods return visitors`() {
		val args = KadxArgs()
		val passes: List<IDexTreeVisitor> = Kadx.getPassesList(args)
		assertThat(passes).isNotEmpty
	}

	private fun assertStaticMethod(name: String, vararg params: Class<*>) {
		val m = checkNotNull(Kadx::class.java.getMethod(name, *params)) { "missing method: $name" }
		assertThat(Modifier.isStatic(m.modifiers))
			.`as`("Kadx.$name must be static for Java plugins")
			.isTrue
	}
}
