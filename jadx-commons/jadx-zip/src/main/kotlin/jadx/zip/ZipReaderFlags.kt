package jadx.zip

import java.util.EnumSet
import java.util.Set

/**
 * Zip 解析器行为标志（可组合使用）。
 */
enum class ZipReaderFlags {
	/**
	 * Search all local file headers by signature without reading
	 * 'central directory' and 'end of central directory' entries
	 * （不读"中央目录/目录尾"项，改为按签名扫描全部本地文件头）
	 */
	IGNORE_CENTRAL_DIR_ENTRIES,

	/**
	 * Enable additional checks to verify zip data and report possible tampering
	 * （启用附加校验来验证 zip 数据并报告可能被篡改）
	 */
	REPORT_TAMPERING,

	/**
	 * Use fallback (java built-in implementation) parser as default.
	 * Custom implementation will be used for '*.apk' files only.
	 * （默认使用 JDK 内置的回退解析器，jadx 自定义解析器只用于 *.apk 文件）
	 */
	FALLBACK_AS_DEFAULT,

	/**
	 * Use only jadx custom parser and do not switch to fallback on errors.
	 * （只用 jadx 自定义解析器，出错时不切换到回退实现）
	 */
	DONT_USE_FALLBACK,

	;

	companion object {
		// @JvmStatic：Java 代码仍可按 ZipReaderFlags.none() 静态调用。
		// 注意：EnumSet.noneOf 是 Java 泛型工厂方法（平台类型），Kotlin 的期望类型匹配对它有 quirk，
		// 直接当表达式返回会报 "Return type mismatch"，需要显式 cast 到 Set
		@JvmStatic fun none(): Set<ZipReaderFlags> = EnumSet.noneOf(ZipReaderFlags::class.java) as Set<ZipReaderFlags>
	}
}
