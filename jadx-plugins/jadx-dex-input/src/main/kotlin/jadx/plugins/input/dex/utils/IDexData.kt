package jadx.plugins.input.dex.utils

/**
 * DEX 数据源接口：提供文件名与完整字节内容。
 *
 * **背景**：[jadx.plugins.input.dex.DexInputPlugin.loadDexData] 的输入单元，
 * 实现可以是内存数组（[SimpleDexData]）或文件包装；解析管线只依赖本接口。
 */
public interface IDexData {

	/** @return DEX 文件名（用于日志与异常信息）*/
	public fun getFileName(): String

	/** @return DEX 文件的完整字节内容 */
	public fun getContent(): ByteArray
}
