package jadx.gui.ui.codearea.sync

/**
 * 代码区域同步的“接收方”接口。
 *
 * **做什么**：当某个代码区域被指定一个同步器 [CodeAreaSyncer] 时，
 * 由该区域决定如何把同步目标定位到自身（例如 Java 区域或 Smali 区域）。
 *
 * **为什么保持接口形态**：Java / Kotlin 双方都可能实现它，签名必须与 Java 完全一致。
 */
interface CodeAreaSyncee {
	/** 使用 [syncer] 同步到本区域；成功返回 true。 */
	fun sync(syncer: CodeAreaSyncer): Boolean
}
