package kadx.gui.device.debugger.smali

/**
 * 单个 smali 寄存器的调试信息。
 *
 * **做什么**：记录寄存器编号、参数名、运行时寄存器编号以及作用域偏移，
 * 是 smali 反汇编结果中「寄存器 -> 变量」映射的基础数据。
 */
class SmaliRegister(private val num: Int, private val endOffsetValue: Int) : RegisterInfo() {

	/** 参数名（方法参数或 `.local` 名）；为空时展示为 `vN`。 */
	private var paramName: String? = null

	/** 作用域起始偏移，初始等于结束偏移（表示尚未使用）。 */
	private var startOffsetValue: Int = endOffsetValue

	/** 是否是方法参数。 */
	private var isParam: Boolean = false

	/** 运行时（JDWP）寄存器编号，由 [ArtAdapter] 换算得到。 */
	private var runtimeNum: Int = 0

	/** @return 运行时寄存器编号 */
	val runtimeRegNum: Int get() = runtimeNum

	/** 设置运行时寄存器编号。 */
	fun setRuntimeRegNum(runtimeNum: Int) {
		this.runtimeNum = runtimeNum
	}

	/**
	 * 判断在 [codeOffset] 处该寄存器是否已初始化。
	 *
	 * 注意：这里用的是开区间 `(start, end)`，与基类的 `[start, end)` 略有不同。
	 */
	override fun isInitialized(codeOffset: Long): Boolean = codeOffset > startOffset && codeOffset < endOffset

	/** 标记该寄存器为方法参数，并记录参数名。 */
	internal fun setParam(name: String) {
		paramName = name
		isParam = true
	}

	/** 把起始偏移更新为更小的值（只允许向前扩展作用域）。 */
	internal fun setStartOffset(off: Int) {
		if (off < startOffsetValue) {
			startOffsetValue = off
		}
	}

	override val name: String get() = paramName ?: "v$num"

	override val regNum: Int get() = num

	override val type: String get() = ""

	override val signature: String? get() = null

	override val startOffset: Int get() = startOffsetValue

	override val endOffset: Int get() = endOffsetValue

	override val isMarkedAsParameter: Boolean get() = isParam
}
