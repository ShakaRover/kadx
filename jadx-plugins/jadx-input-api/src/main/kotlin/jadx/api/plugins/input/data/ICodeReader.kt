package jadx.api.plugins.input.data

import jadx.api.plugins.input.insns.InsnData
import java.util.function.Consumer

/**
 * 方法代码读取器接口：遍历一个方法的指令序列。
 *
 * **背景**：输入插件把每个方法的字节码解析成本接口的实现，jadx-core 通过
 * [visitInstructions] 逐条拉取 [InsnData] 构建 IR 图。
 */
public interface ICodeReader {

	/** @return 本读取器的独立副本（修改副本不影响原对象）*/
	public fun copy(): ICodeReader

	/**
	 * 遍历方法的所有指令，每发现一条就回调 [insnConsumer]。
	 * @param insnConsumer 接收每条 [InsnData] 的消费者
	 */
	public fun visitInstructions(insnConsumer: Consumer<InsnData>)

	/** @return 方法使用的寄存器总数（Dex 的 registers_size）*/
	public val registersCount: Int

	/** @return 参数起始寄存器编号（具体语义由输入格式决定）*/
	public val argsStartReg: Int

	/** @return 指令单元数 */
	public val unitsCount: Int

	/** @return 调试信息；无调试信息时为 null */
	public val debugInfo: IDebugInfo?

	/** @return 方法代码在输入文件中的起始偏移 */
	public val codeOffset: Int

	/** @return try 块列表（异常处理区间，可为空列表）*/
	public val tries: List<ITry>
}
