package jadx.gui.ui.codearea.sync

/**
 * 代码区域同步器总接口：同时支持“同步到 Java 区域”和“同步到 Smali 区域”。
 *
 * **做什么**：把两个方向的策略接口组合起来，作为代码区域同步的统一类型。
 */
interface CodeAreaSyncer :
	IToJavaSyncStrategy,
	IToSmaliSyncStrategy
