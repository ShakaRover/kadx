package jadx.core.export.gen

import jadx.core.dex.nodes.RootNode
import jadx.core.utils.files.FileUtils

/**
 * Gradle 生成器共用的小工具集。
 *
 * **用途**：从输入文件推断工程名，供生成的 `settings.gradle` 使用。
 *
 * **Kotlin 转换说明**：原 Java 的静态方法改写为 `object` 单例，
 * 调用方仍写 `GradleGeneratorTools.guessProjectName(root)`。
 */
object GradleGeneratorTools {

	/** 推断工程名：只有一个输入文件时用其文件名，否则回退为 `PROJECT_NAME`。 */
	fun guessProjectName(root: RootNode): String {
		val inputFiles = root.getArgs().inputFiles
		if (inputFiles.size == 1) {
			return FileUtils.getPathBaseName(inputFiles[0].toPath())
		}
		// 默认工程名
		return "PROJECT_NAME"
	}
}
