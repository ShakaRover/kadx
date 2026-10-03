package jadx.zip.security

import jadx.zip.IZipEntry
import java.io.File

/**
 * Zip 安全策略接口：在解析 zip 时校验条目是否可信，防止恶意构造的 zip（如路径穿越）危害宿主进程。
 */
interface IJadxZipSecurity {
	/** 检查 zip 条目是否有效且可安全处理 */
	fun isValidEntry(entry: IZipEntry): Boolean

	/**
	 * 检查 zip 条目名是否合法。
	 * 该校验应当是 [isValidEntry] 方法的一部分。
	 */
	fun isValidEntryName(entryName: String): Boolean

	/** 是否对条目的解压数据使用限流 InputStream（配合防 zip 炸弹） */
	fun useLimitedDataStream(): Boolean

	/** 期望的 zip 内条目数上限，超过则打开失败；返回 -1 表示禁用条目数量检查 */
	val maxEntriesCount: Int

	/** 系统解析路径后该文件是否仍位于 baseDir 内部（防 ../ 路径穿越） */
	fun isInSubDirectory(baseDir: File, file: File): Boolean
}
