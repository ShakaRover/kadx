package jadx.plugins.input.dex

/**
 * DEX 输入插件通用运行时异常。
 *
 * **背景**：DEX 校验和验证失败（DexCheckSum）、读取输入流失败（[DexInputPlugin.loadDexFromInputStream]）
 * 以及 DEX 结构解析遇到非法数据（LEB128 / MUTF-8 / 注解编码等）时抛出。
 * 继承 RuntimeException（非受检异常），Java 调用方无需声明 throws；
 * 保留与原 Java 版一致的两个构造签名：`(message)` 与 `(message, cause)`，保证 Java 调用方零改动。
 */
public class DexException : RuntimeException {
	constructor(message: String) : super(message)

	constructor(message: String, cause: Throwable) : super(message, cause)

	private companion object {
		private const val serialVersionUID: Long = -5575702801815409269L
	}
}
