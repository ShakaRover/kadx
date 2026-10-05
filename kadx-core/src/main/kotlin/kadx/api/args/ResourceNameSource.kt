package kadx.api.args

/**
 * 资源原始名称的来源（用于去混淆时挑选更可读的名字）。
 *
 * 公共 API：kadx-cli / kadx-gui 通过该枚举配置资源重命名策略，常量名与顺序保持不变。
 */
enum class ResourceNameSource {

	/**
	 * 自动选择最佳名称（默认）。
	 */
	AUTO,

	/**
	 * 强制使用资源自身提供的名称。
	 */
	RESOURCES,

	/**
	 * 强制使用 R 类中记录的资源名称。
	 */
	CODE,
}
