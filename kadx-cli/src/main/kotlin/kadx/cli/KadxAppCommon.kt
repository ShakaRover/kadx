package kadx.cli

import kadx.api.KadxArgs
import kadx.api.security.KadxSecurityFlag
import kadx.api.security.impl.KadxSecurity
import kadx.commons.app.KadxCommonEnv
import kadx.zip.security.DisabledZipSecurity
import kadx.zip.security.IKadxZipSecurity
import kadx.zip.security.KadxZipSecurity

/**
 * 把环境变量里的安全相关配置应用到 [KadxArgs]。
 *
 * **做什么**：读取 `KADX_DISABLE_ALL_SECURITY_FLAGS`、`KADX_DISABLE_XML_SECURITY`、
 * `KADX_DISABLE_ZIP_SECURITY`、`KADX_ZIP_MAX_ENTRIES_COUNT` 等环境变量，
 * 决定启用哪些 [KadxSecurityFlag] 以及 zip 安全检查参数。
 *
 * **为什么这样写**：原 Java 是纯静态工具方法，Java 调用方（kadx-gui 的 `KadxWrapper`）
 * 以 `KadxAppCommon.applyEnvVars(...)` 调用，因此放进 `companion object`。
 */
class KadxAppCommon {

	companion object {
		/**
		 * 根据环境变量构造 [KadxSecurity] 并写入 [kadxArgs]。
		 *
		 * @param kadxArgs 需要写入安全配置的 kadx 参数对象
		 */
		fun applyEnvVars(kadxArgs: KadxArgs) {
			val zipSecurity: IKadxZipSecurity
			// 注意：KadxSecurityFlag.all()/none() 返回只读 Set，这里转成可变集合后再增删
			val flags: MutableSet<KadxSecurityFlag>

			if (KadxCommonEnv.getBool("KADX_DISABLE_ALL_SECURITY_FLAGS", false)) {
				flags = KadxSecurityFlag.none().toMutableSet()
			} else {
				flags = KadxSecurityFlag.all().toMutableSet()
				if (KadxCommonEnv.getBool("KADX_DISABLE_XML_SECURITY", false)) {
					flags.remove(KadxSecurityFlag.SECURE_XML_PARSER)
					// TODO: 与 XML 安全无关，但为兼容旧行为保留此联动
					flags.remove(KadxSecurityFlag.VERIFY_APP_PACKAGE)
				}
			}

			val disableZipSecurity = KadxCommonEnv.getBool("KADX_DISABLE_ZIP_SECURITY", false)
			if (disableZipSecurity) {
				flags.remove(KadxSecurityFlag.SECURE_ZIP_READER)
				zipSecurity = DisabledZipSecurity.INSTANCE
			} else {
				val kadxZipSecurity = KadxZipSecurity()
				val maxZipEntriesCount = KadxCommonEnv.getInt("KADX_ZIP_MAX_ENTRIES_COUNT", -2)
				if (maxZipEntriesCount != -2) {
					kadxZipSecurity.setMaxEntriesCount(maxZipEntriesCount)
				}
				val zipBombMinUncompressedSize = KadxCommonEnv.getInt("KADX_ZIP_BOMB_MIN_UNCOMPRESSED_SIZE", -2)
				if (zipBombMinUncompressedSize != -2) {
					kadxZipSecurity.zipBombMinUncompressedSize = zipBombMinUncompressedSize
				}
				val setZipBombDetectionFactor = KadxCommonEnv.getInt("KADX_ZIP_BOMB_DETECTION_FACTOR", -2)
				if (setZipBombDetectionFactor != -2) {
					kadxZipSecurity.zipBombDetectionFactor = setZipBombDetectionFactor
				}
				zipSecurity = kadxZipSecurity
			}
			kadxArgs.security = KadxSecurity(flags, zipSecurity)
		}
	}
}
