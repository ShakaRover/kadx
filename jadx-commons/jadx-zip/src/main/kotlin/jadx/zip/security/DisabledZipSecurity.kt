package jadx.zip.security

import jadx.zip.IZipEntry
import java.io.File

/**
 * 不做任何校验的安全策略（用于用户显式关闭安全特性时）。
 *
 * INSTANCE 是单例：Kotlin 侧按 `DisabledZipSecurity.INSTANCE` 访问。
 */
class DisabledZipSecurity : IJadxZipSecurity {
	companion object {
		/** 全局唯一实例（对应原 Java 的 public static final INSTANCE） */
		val INSTANCE = DisabledZipSecurity()
	}

	override fun isValidEntry(entry: IZipEntry): Boolean = true
	override fun isValidEntryName(entryName: String): Boolean = true
	override fun useLimitedDataStream(): Boolean = false
	override fun getMaxEntriesCount(): Int = -1 // -1 表示禁用条目数量检查
	override fun isInSubDirectory(baseDir: File, file: File): Boolean = true
}
