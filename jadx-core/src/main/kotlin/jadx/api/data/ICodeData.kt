package jadx.api.data

/**
 * 用户“代码数据”的公共接口：一个项目里保存的全部注释与重命名。
 *
 * **做什么**：jadx-gui 的工程文件（`JadxProject`）与 CLI 的映射导出都依赖它；
 * [getComments] / [getRenames] 分别返回注释列表与重命名列表，[isEmpty] 用于快速判空。
 *
 * **为什么用 Kotlin `List`**：JVM 擦除后仍是 `java.util.List`，Java 实现与调用方零改动；
 * Kotlin 侧则用只读 `List`，避免误改（需要修改时调用方自行持有可变实现）。
 */
interface ICodeData {

	/** 全部注释。 */
	fun getComments(): List<ICodeComment>

	/** 全部重命名记录。 */
	fun getRenames(): List<ICodeRename>

	/** 是否既没有注释也没有重命名。 */
	fun isEmpty(): Boolean
}
