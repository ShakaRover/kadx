package kadx.gui.settings

/**
 * 更新渠道：决定检查更新时使用稳定版还是非稳定版。
 *
 * **注意**：枚举常量名与顺序被持久化配置引用（Gson 默认按名称序列化），不可更改。
 */
enum class KadxUpdateChannel {
	STABLE,
	UNSTABLE,
}
