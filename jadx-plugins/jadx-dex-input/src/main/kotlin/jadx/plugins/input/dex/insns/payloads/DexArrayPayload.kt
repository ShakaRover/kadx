package jadx.plugins.input.dex.insns.payloads

import jadx.api.plugins.input.insns.custom.IArrayPayload

/**
 * DEX `fill-array-data` 指令的数组载荷实现。
 *
 * **背景**：`fill-array-data` 指令后跟一段原始数组数据（元素大小可为
 * 1/2/4/8 字节），[jadx.plugins.input.dex.insns.DexInsnFormat.FORMAT_FILL_ARRAY_DATA_PAYLOAD]
 * 解码时把数据读入对应类型的数组并封装为本对象，挂到指令的 payload 上。
 *
 * @param size 元素个数
 * @param elemSize 单个元素的字节大小（1/2/4/8）
 * @param data 数组数据本体，具体类型由 [elemSize] 决定（ByteArray/ShortArray/IntArray/LongArray）
 */
public class DexArrayPayload(
	private val size: Int,
	private val elemSize: Int,
	private val data: Any?,
) : IArrayPayload {

	override fun getSize(): Int = size

	override fun getElementSize(): Int = elemSize

	override fun getData(): Any? = data
}
