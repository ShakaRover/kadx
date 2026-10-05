package kadx.core.export

import kadx.core.utils.files.FileUtils
import java.io.File

/**
 * 导出工程的输出目录集合。
 *
 * **用途**：保存源码输出目录与资源输出目录，导出前统一调用 [makeDirs] 建目录。
 *
 * **Kotlin 转换说明**：原 Java 的 `getSrcOutDir()` / `getResOutDir()` 由 Kotlin 属性
 * [srcOutDir] / [resOutDir] 自动生成同名 getter，Java 调用方零改动。
 */
class OutDirs(
	val srcOutDir: File,
	val resOutDir: File,
) {

	/** 创建源码与资源两个输出目录（已存在则忽略）。 */
	fun makeDirs() {
		FileUtils.makeDirs(srcOutDir)
		FileUtils.makeDirs(resOutDir)
	}
}
