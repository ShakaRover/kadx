package jadx.plugins.input.java.data.attributes.types.data

/**
 * BootstrapMethods attribute 中单个条目的原始形式。
 *
 **做什么**：保存 method handle 的常量池索引和 args 索引数组（尚未解析成对象），
 * 由 [jadx.plugins.input.java.data.ConstPoolReader] 在真正需要 invoke-dynamic 信息时再解码，
 * 避免加载阶段做无用功。
 */
class RawBootstrapMethod(
	/** method handle 的常量池索引 */
	val methodHandleIdx: Int,
	/** 静态引用参数（constant arguments）的常量池索引数组 */
	val args: IntArray,
)
