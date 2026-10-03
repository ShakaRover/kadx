package jadx.core.export.gen

import jadx.core.export.OutDirs

/**
 * Gradle 工程生成器接口。
 *
 * **用途**：抽象出不同导出类型（Android App / Android Library / 纯 Java）的公共流程：
 * 先 [init] 计算输出目录，再 [generateFiles] 写出 Gradle 脚本，最后通过 [outDirs] 取回目录。
 *
 * **Kotlin 转换说明**：保持为普通 Kotlin 接口，方法签名与原 Java 完全一致，
 * Java 实现类仍可直接 `implements` 并实现这三个方法。
 */
interface IExportGradleGenerator {

	/** 初始化：计算并创建输出目录。 */
	fun init()

	/** 返回输出目录（必须在 [init] 之后调用）。 */
	val outDirs: OutDirs

	/** 生成 Gradle 相关文件。 */
	fun generateFiles()
}
