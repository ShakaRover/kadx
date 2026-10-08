package kadx.gui.settings

import kadx.gui.cache.code.CodeCacheMode
import kadx.gui.utils.NLS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * 代码缓存模式的反序列化健壮性测试。
 *
 * 背景：设置里的 `codeCacheMode` 声明为非空枚举，但 Gson 遇到**未知枚举值**时会把它置为
 * `null`（反射绕过 Kotlin 空检查）。若不修正，`registerCodeCache` 拿不到有效模式，
 * `args.codeCache` 会静默保留 `KadxArgs` 默认的**无界** `InMemoryCodeCache`。
 * [KadxSettings.fixOnLoad] 负责把它回退为 DISK。
 */
class KadxSettingsCacheModeTest {

	private fun loadFrom(json: String): KadxSettings = KadxSettings(KadxSettings.buildConfigAdapter()).also { it.loadSettingsFromJsonString(json) }

	@Test
	fun unknownCodeCacheModeFallsBackToDisk() {
		val settings = loadFrom("""{"codeCacheMode":"NOT_A_REAL_MODE"}""")
		assertThat(settings.codeCacheMode).isEqualTo(CodeCacheMode.DISK)
	}

	@Test
	fun missingCodeCacheModeKeepsDefault() {
		val settings = loadFrom("{}")
		assertThat(settings.codeCacheMode).isEqualTo(CodeCacheMode.DISK)
	}

	@Test
	fun validCodeCacheModeIsPreserved() {
		assertThat(loadFrom("""{"codeCacheMode":"MEMORY"}""").codeCacheMode)
			.isEqualTo(CodeCacheMode.MEMORY)
		assertThat(loadFrom("""{"codeCacheMode":"DISK_WITH_CACHE"}""").codeCacheMode)
			.isEqualTo(CodeCacheMode.DISK_WITH_CACHE)
	}

	companion object {
		/**
		 * Gson 反序列化设置时会反射枚举常量（`EnumTypeAdapter`），其中 `ActionModel` 等枚举
		 * 在类初始化时就要取 NLS 文案，因此必须先初始化 NLS，否则会抛
		 * `UninitializedPropertyAccessException: localizedMessagesMap`。
		 */
		@BeforeAll
		@JvmStatic
		fun initNls() {
			NLS.setLocale(NLS.defaultLocale())
		}
	}
}
