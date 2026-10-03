package jadx.api.plugins.input.insns

import jadx.api.plugins.input.data.ICallSite
import jadx.api.plugins.input.data.IFieldRef
import jadx.api.plugins.input.data.IMethodHandle
import jadx.api.plugins.input.data.IMethodProto
import jadx.api.plugins.input.data.IMethodRef
import jadx.api.plugins.input.insns.custom.ICustomPayload

/**
 * 指令数据接口：一条已解析的机器指令（Dex / class file）。
 *
 * **背景**：输入插件把每条字节码指令封装成本接口的实现，jadx-core 通过
 * [ICodeReader.visitInstructions] 逐条接收并解码为 IR。
 *
 * **可空约定**：getIndexAsXxx() 系列方法只在指令的 index 类型匹配时才有值，
 * 不匹配或输入格式不支持时返回 null（原 Java 无注解，按实际行为标注）。
 */
public interface InsnData {

	/** 解码指令操作数（惰性解析入口）*/
	public fun decode()

	/** @return 方法体内的指令偏移 */
	public val offset: Int

	/** @return 输入文件中的字节偏移 */
	public val fileOffset: Int

	/** @return 操作码枚举 */
	public val opcode: Opcode

	/** @return 操作码助记符（如 "add-int"）；未知操作码时可能为 null */
	public val opcodeMnemonic: String?

	/** @return 指令原始字节 */
	public val byteCode: ByteArray

	/** @return index 操作数的类型（见 [InsnIndexType]，决定用哪个 getIndexAsXxx 取值）*/
	public val indexType: InsnIndexType

	/** @return 未解码的原始操作码单元值 */
	public val rawOpcodeUnit: Int

	/** @return 该指令使用的寄存器个数 */
	public val regsCount: Int

	/**
	 * @param argNum 第几个寄存器操作数（从 0 开始）
	 * @return 对应寄存器的编号
	 */
	public fun getReg(argNum: Int): Int

	/**
	 * @return 结果寄存器编号；不需要时返回 -1。
	 * **背景**：某些输入格式没有 move-result 指令，用此方法直接指定结果寄存器。
	 */
	public val resultReg: Int

	/** @return 字面量操作数（如 const/4 的立即数）*/
	public val literal: Long

	/** @return 跳转目标偏移（goto/if 类指令）*/
	public val target: Int

	/** @return index 操作数的原始值 */
	public val index: Int

	/** @return index 作为字符串取值；类型不匹配时为 null */
	public val indexAsString: String?

	/** @return index 作为类型名取值（如 const-class）；类型不匹配时为 null */
	public val indexAsType: String?

	/** @return index 作为字段引用取值（iget/put 类指令）；类型不匹配时为 null */
	public val indexAsField: IFieldRef?

	/** @return index 作为方法引用取值（invoke 类指令）；类型不匹配时为 null */
	public val indexAsMethod: IMethodRef?

	/** @return index 作为调用点取值（invoke-custom）；类型不匹配时为 null */
	public val indexAsCallSite: ICallSite?

	/**
	 * @param protoIndex proto 表索引
	 * @return index 作为方法原型取值（const-method-type / invoke-polymorphic）；不支持时为 null
	 */
	public fun getIndexAsProto(protoIndex: Int): IMethodProto?

	/** @return index 作为方法句柄取值（const-method-handle）；不支持时为 null */
	public val indexAsMethodHandle: IMethodHandle?

	/** @return 指令附带的自定义载荷（switch 表 / 数组数据等）；无载荷时为 null */
	public val payload: ICustomPayload?
}
