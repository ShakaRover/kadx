package kadx.api.plugins.options

/**
 * 插件选项的附加标志位（枚举）。
 *
 * **用途**：控制 kadx-gui 里选项的展示/存储行为，以及是否影响代码缓存。
 */
enum class OptionFlag {
	/**
	 * 存入项目设置而非全局设置（仅 kadx-gui）。
	 */
	PER_PROJECT,

	/**
	 * 在 kadx-gui 中不显示该选项（适用于有自定义 UI 的选项）。
	 */
	HIDE_IN_GUI,

	/**
	 * 在 kadx-gui 中只读（可用于计算得出的属性）。
	 */
	DISABLE_IN_GUI,

	/**
	 * 仅当该选项不影响生成代码时添加。
	 * 添加后，修改该选项不会触发代码缓存重置。
	 */
	NOT_CHANGING_CODE,
}
