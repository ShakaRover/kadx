package jadx.plugins.input.java.data.code.trycatch

import org.jetbrains.annotations.Nullable

/**
 * 异常表中的单个 catch 子句。
 *
 **做什么**：保存 handler 字节码偏移与捕获的异常类型（内部形式类名）；
 * type 为 null 表示 catch-all（无 catch_type 的兜底子句）。
 */
class JavaSingleCatch(
	/** 异常处理入口的字节码偏移 */
	val handler: Int,
	/** 捕获的异常类型；null = catch-all */
	@Nullable
	val type: String?,
)
