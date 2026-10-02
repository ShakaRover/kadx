package jadx.api.data

/**
 * 代码引用类型：表示一条注释/重命名记录“挂”在方法内的哪个位置。
 *
 * **做什么**：jadx 允许用户对方法参数、局部变量、catch 处理器、指令单独做注释或重命名，
 * 这个枚举区分这四种目标。
 *
 * 这是公共 API（会被 jadx-gui / 插件序列化），枚举常量名必须保持不变。
 */
enum class CodeRefType {
	/** 方法参数。 */
	MTH_ARG,

	/** 局部变量。 */
	VAR,

	/** catch 处理器。 */
	CATCH,

	/** 单条指令（按字节码偏移定位）。 */
	INSN,
}
