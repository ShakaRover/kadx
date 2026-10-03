package jadx.cli

import jadx.api.JadxArgs
import jadx.api.security.JadxSecurityFlag
import jadx.api.security.impl.JadxSecurity
import jadx.commons.app.JadxCommonEnv
import jadx.zip.security.DisabledZipSecurity
import jadx.zip.security.IJadxZipSecurity
import jadx.zip.security.JadxZipSecurity

/**
 * 把环境变量里的安全相关配置应用到 [JadxArgs]。
 *
 * **做什么**：读取 `JADX_DISABLE_ALL_SECURITY_FLAGS`、`JADX_DISABLE_XML_SECURITY`、
 * `JADX_DISABLE_ZIP_SECURITY`、`JADX_ZIP_MAX_ENTRIES_COUNT` 等环境变量，
 * 决定启用哪些 [JadxSecurityFlag] 以及 zip 安全检查参数。
 *
 * **为什么这样写**：原 Java 是纯静态工具方法，Java 调用方（jadx-gui 的 `JadxWrapper`）
 * 以 `JadxAppCommon.applyEnvVars(...)` 调用，因此放进 `companion object`。
 */
class JadxAppCommon {

	companion object {
		/**
		 * 根据环境变量构造 [JadxSecurity] 并写入 [jadxArgs]。
		 *
		 * @param jadxArgs 需要写入安全配置的 jadx 参数对象
		 */
		fun applyEnvVars(jadxArgs: JadxArgs) {
			val zipSecurity: IJadxZipSecurity
			// 注意：JadxSecurityFlag.all()/none() 返回只读 Set，这里转成可变集合后再增删
			val flags: MutableSet<JadxSecurityFlag>

			if (JadxCommonEnv.getBool("JADX_DISABLE_ALL_SECURITY_FLAGS", false)) {
				flags = JadxSecurityFlag.none().toMutableSet()
			} else {
				flags = JadxSecurityFlag.all().toMutableSet()
				if (JadxCommonEnv.getBool("JADX_DISABLE_XML_SECURITY", false)) {
					flags.remove(JadxSecurityFlag.SECURE_XML_PARSER)
					// TODO: 与 XML 安全无关，但为兼容旧行为保留此联动
					flags.remove(JadxSecurityFlag.VERIFY_APP_PACKAGE)
				}
			}

			val disableZipSecurity = JadxCommonEnv.getBool("JADX_DISABLE_ZIP_SECURITY", false)
			if (disableZipSecurity) {
				flags.remove(JadxSecurityFlag.SECURE_ZIP_READER)
				zipSecurity = DisabledZipSecurity.INSTANCE
			} else {
				val jadxZipSecurity = JadxZipSecurity()
				val maxZipEntriesCount = JadxCommonEnv.getInt("JADX_ZIP_MAX_ENTRIES_COUNT", -2)
				if (maxZipEntriesCount != -2) {
					jadxZipSecurity.setMaxEntriesCount(maxZipEntriesCount)
				}
				val zipBombMinUncompressedSize = JadxCommonEnv.getInt("JADX_ZIP_BOMB_MIN_UNCOMPRESSED_SIZE", -2)
				if (zipBombMinUncompressedSize != -2) {
					jadxZipSecurity.zipBombMinUncompressedSize = zipBombMinUncompressedSize
				}
				val setZipBombDetectionFactor = JadxCommonEnv.getInt("JADX_ZIP_BOMB_DETECTION_FACTOR", -2)
				if (setZipBombDetectionFactor != -2) {
					jadxZipSecurity.zipBombDetectionFactor = setZipBombDetectionFactor
				}
				zipSecurity = jadxZipSecurity
			}
			jadxArgs.security = JadxSecurity(flags, zipSecurity)
		}
	}
}
