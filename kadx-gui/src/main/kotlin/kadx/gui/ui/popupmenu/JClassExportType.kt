@file:Suppress("ktlint:standard:enum-entry-name-case")

package kadx.gui.ui.popupmenu

/**
 * 类/包导出时使用的代码类型。
 *
 * **做什么**：右键菜单「导出」子菜单会为每种类型生成一个菜单项；
 * [extension] 决定保存文件时使用的扩展名（`java` 或 `smali`）。
 *
 * **为什么保留大写开头的枚举常量名**：原 Java 枚举常量即为 `Code`/`Smali`/...，
 * 迁移要求类名、常量名保持不变，故用文件级 `@Suppress` 关闭 ktlint 的枚举命名检查。
 */
enum class JClassExportType(
	/** 导出文件的扩展名（不含点）。 */
	val extension: String,
) {
	/** 反编译得到的 Java 代码。 */
	Code("java"),

	/** Smali 汇编代码。 */
	Smali("smali"),

	/** 简单模式（`DecompilationMode.SIMPLE`）反编译结果。 */
	Simple("java"),

	/** 回退模式（`DecompilationMode.FALLBACK`）反编译结果。 */
	Fallback("java"),
}
