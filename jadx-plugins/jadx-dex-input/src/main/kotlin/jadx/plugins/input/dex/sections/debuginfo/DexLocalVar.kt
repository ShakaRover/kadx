package jadx.plugins.input.dex.sections.debuginfo

import jadx.api.plugins.input.data.ILocalVar
import jadx.api.plugins.utils.Utils
import jadx.plugins.input.dex.sections.SectionReader
import org.jetbrains.annotations.Nullable

/**
 * DEX 调试信息中的局部变量（含方法参数）。
 *
 * **背景**：[DebugInfoParser] 解析 local_debug_info section 时为每个变量创建本对象，
 * 通过 [start]/[end] 记录作用域指令偏移；jadx-core 经 [ILocalVar] 接口读取以还原源码变量名。
 *
 * **Kotlin 转换说明**：[ILocalVar] 是 Kotlin 接口（抽象函数声明）→ 全部显式 `override fun`。
 * name/type 在 nameId/typeId 为 NO_INDEX 时运行时确实可能为 null（与原 Java 一致），
 * getter 用 checkNotNull 兜底（同 DexMethodRef 的既有模式）。
 */
public class DexLocalVar(
	private val regNum: Int,
	@Nullable private val name: String?,
	@Nullable private val type: String?,
	@Nullable private val sign: String?,
) : ILocalVar {

	companion object {
		private const val PARAM_START_OFFSET = -1
	}

	/** 从 section 读取器按索引解析名称/类型/签名构造 */
	public constructor(
		dex: SectionReader,
		regNum: Int,
		nameId: Int,
		typeId: Int,
		signId: Int,
	) : this(regNum, dex.getString(nameId), dex.getType(typeId), dex.getString(signId))

	/** 无签名信息的便捷构造 */
	public constructor(
		regNum: Int,
		name: String?,
		type: String?,
	) : this(regNum, name, type, null)

	private var isEnd = false
	private var startOffset = 0
	private var endOffset = 0

	/** 标记变量作用域起点 */
	public fun start(addr: Int) {
		isEnd = false
		startOffset = addr
	}

	/**
	 * 设置变量作用域终点。
	 * @param addr 结束指令偏移
	 * @return 变量此前处于激活状态返回 true，已结束时返回 false
	 */
	public fun end(addr: Int): Boolean {
		if (isEnd) {
			return false
		}
		isEnd = true
		endOffset = addr
		return true
	}

	override fun getRegNum(): Int = regNum

	override fun getName(): String = checkNotNull(name) { "local var name is not set" }

	override fun getType(): String = checkNotNull(type) { "local var type is not set" }

	@Nullable
	override fun getSignature(): String? = sign

	override fun getStartOffset(): Int = startOffset

	/** 标记为方法参数（起始偏移记为 [PARAM_START_OFFSET]）*/
	public fun markAsParameter() {
		startOffset = PARAM_START_OFFSET
	}

	override fun isMarkedAsParameter(): Boolean = startOffset == PARAM_START_OFFSET

	override fun getEndOffset(): Int = endOffset

	public fun isEnd(): Boolean = isEnd

	// equals/hashCode 与原 Java 一致：显式委托 super（即身份比较），Kotlin 默认行为相同，故省略

	override fun toString(): String = (if (startOffset == -1) "-1 " else Utils.formatOffset(startOffset)) +
		'-' + (if (isEnd) Utils.formatOffset(endOffset) else "      ") +
		": r$regNum '$name' $type" +
		(if (sign != null) ", signature: $sign" else "")
}
