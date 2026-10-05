package kadx.api.plugins.input.insns.custom

/**
 * switch 跳转表载荷接口：表示 packed-switch / sparse-switch 指令的键值与目标。
 *
 * **背景**：Dex 的 `packed-switch`（连续键）和 `sparse-switch`（稀疏键）指令
 * 后面跟着一段跳转表数据，解析后封装为本接口的实现。
 */
public interface ISwitchPayload : ICustomPayload {

	/** @return 条目数（keys/targets 的长度）*/
	public val size: Int

	/** @return switch 的键值数组 */
	public val keys: IntArray

	/** @return 各键对应的跳转目标偏移数组，与 [getKeys] 一一对应 */
	public val targets: IntArray
}
