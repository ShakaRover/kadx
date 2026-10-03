package jadx.api.plugins.input.insns.custom

/**
 * 数组载荷接口：表示指令携带的数组数据块（如 Dex 的 fill-array-data）。
 *
 * **背景**：Dex 中 `fill-array-data` / `fill-array-data-range` 指令后面跟着一段
 * 原始数组数据，解析后封装为本接口的实现。
 */
public interface IArrayPayload : ICustomPayload {

	/** @return 元素个数 */
	public val size: Int

	/** @return 单个元素的字节大小（如 int 为 4）*/
	public val elementSize: Int

	/**
	 * @return 数组数据本体，具体类型由输入格式决定（通常为 Object[]）。
	 * **可空**：原 Java 返回 Object 且未声明非空，实现类可能持有 null。
	 */
	public val data: Any?
}
