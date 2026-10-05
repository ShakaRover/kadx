package kadx.gui.ui.codearea.sync

/**
 * 代码区域同步器工厂。
 *
 * **做什么**：代码区域实现本接口后，就能按自身类型（Java / Smali）创建对应的
 * [CodeAreaSyncer]。
 */
interface CodeAreaSyncerAbstractFactory {
	/** 创建适用于本代码区域的同步器。 */
	fun createCodeAreaSyncer(): CodeAreaSyncer
}
