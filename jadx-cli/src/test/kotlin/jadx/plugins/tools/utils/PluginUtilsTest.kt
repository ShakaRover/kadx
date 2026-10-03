package jadx.plugins.tools.utils

import jadx.plugins.tools.utils.PluginUtils.extractVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [PluginUtils.extractVersion] 的版本号解析测试。
 */
class PluginUtilsTest {

	@Test
	fun testExtractVersion() {
		assertThat(extractVersion("plugin-name-v1.2.3.jar")).isEqualTo("1.2.3")
		assertThat(extractVersion("plugin-name-v1.2.jar")).isEqualTo("1.2")
		assertThat(extractVersion("1.2.3.jar")).isEqualTo("1.2.3")
	}
}
