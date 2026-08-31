package jadx.api.plugins.input.insns.custom.impl

import jadx.api.plugins.input.insns.custom.ISwitchPayload

/**
 * switch 跳转表载荷的默认实现。
 *
 * **使用场景**：jadx-java-input 解析 class file 的 lookupswitch / tableswitch
 * 指令时创建本对象，挂到 [jadx.api.plugins.input.insns.InsnData.payload] 上。
 *
 * @param size 条目数（keys/targets 的长度）
 * @param keys switch 键值数组
 * @param targets 各键对应的跳转目标偏移数组，与 [keys] 一一对应
 *
 * **Kotlin 转换说明**：原 Java 用私有 final 字段 + getter。这里保持同样的结构——
 * 主构造器参数为私有属性（不生成公开访问器），再写显式 `override fun` 实现接口方法。
 * 注意：**Kotlin 属性不能覆写 Kotlin 接口里声明的抽象函数**（实测 'overrides nothing'，
 * 该规则只对 Java getter 生效），所以必须用显式函数形式；字节码与原 Java 完全一致。
 */
public class SwitchPayload(
	/** 条目数 */
	private val size: Int,
	/** switch 键值数组 */
	private val keys: IntArray,
	/** 各键对应的跳转目标偏移数组 */
	private val targets: IntArray,
) : ISwitchPayload {

	override fun getSize(): Int = size

	override fun getKeys(): IntArray = keys

	override fun getTargets(): IntArray = targets
}
