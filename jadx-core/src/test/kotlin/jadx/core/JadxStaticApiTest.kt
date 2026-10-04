package jadx.core

import jadx.api.JadxArgs
import jadx.core.dex.visitors.IDexTreeVisitor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

/**
 * 回归测试：锁定 `Jadx` 对**外部 Java 插件**暴露的 JVM 静态表面。
 *
 * 背景（OSS 反编译实测发现）：`Jadx` 是插件可见的工具类，随 jadx 分发的
 * `example-plugin` 会以**静态方式**调用 `jadx.core.Jadx.getVersion()`。
 * 在 Kotlin 化 / 现代化清理 `@JvmStatic` 时若误删，运行时会抛
 * `IncompatibleClassChangeError: Expected static method 'jadx.core.Jadx.getVersion()'`，
 * 导致插件初始化失败、整个反编译流程中断。
 *
 * 这里用反射把这些成员的静态性固定下来，防止再次回归。
 */
class JadxStaticApiTest {

	@Test
	fun `plugin facing methods are static on JVM`() {
		assertStaticMethod("getVersion")
		assertStaticMethod("getPassesList", JadxArgs::class.java)
		assertStaticMethod("getRegionsModePasses", JadxArgs::class.java)
		assertStaticMethod("getSimpleModePasses", JadxArgs::class.java)
		assertStaticMethod("isDevVersion")
	}

	@Test
	fun `plugin facing properties expose static getters`() {
		assertStaticMethod("getPreDecompilePassesList")
		assertStaticMethod("getFallbackPassesList")
	}

	@Test
	fun `pass list methods return visitors`() {
		val args = JadxArgs()
		val passes: List<IDexTreeVisitor> = Jadx.getPassesList(args)
		assertThat(passes).isNotEmpty
	}

	private fun assertStaticMethod(name: String, vararg params: Class<*>) {
		val m = checkNotNull(Jadx::class.java.getMethod(name, *params)) { "missing method: $name" }
		assertThat(Modifier.isStatic(m.modifiers))
			.`as`("Jadx.$name must be static for Java plugins")
			.isTrue
	}
}
