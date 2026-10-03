package jadx.core.plugins

import jadx.api.plugins.gui.JadxGuiContext
import jadx.core.plugins.files.IJadxFilesGetter

/**
 * 插件运行时的应用级上下文：GUI 上下文与文件目录提供者。
 *
 * **做什么**：把「当前是否运行在 GUI 环境」以及「缓存/配置/临时目录从哪来」
 * 这两类应用级依赖，注入给每个插件。
 *
 * **Kotlin 转换说明**：公共 getter 保留显式函数（`getGuiContext` / `getFilesGetter`），
 * Java 调用方（jadx-gui 的 `CollectPlugins`、`JadxWrapper`）零改动。
 * `getFilesGetter` 内部在未设置时显式抛出 [NullPointerException]，复刻原 Java 在未设置时抛 NPE 的行为。
 */
class AppContext {

	/** GUI 上下文；非 GUI 环境下为 null。 */
	private var guiContext: JadxGuiContext? = null

	/** 文件目录提供者；使用前必须由外部 set。 */
	private var filesGetter: IJadxFilesGetter? = null

	fun getGuiContext(): JadxGuiContext? = guiContext

	fun setGuiContext(guiContext: JadxGuiContext?) {
		this.guiContext = guiContext
	}

	/** 获取文件目录提供者；未设置时抛出 NPE（与原 Java 的 `Objects.requireNonNull` 一致）。 */
	fun getFilesGetter(): IJadxFilesGetter = filesGetter
		?: throw NullPointerException("filesGetter is not set")

	fun setFilesGetter(filesGetter: IJadxFilesGetter) {
		this.filesGetter = filesGetter
	}
}
