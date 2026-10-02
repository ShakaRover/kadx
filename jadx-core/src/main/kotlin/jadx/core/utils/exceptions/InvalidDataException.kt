package jadx.core.utils.exceptions

/**
 * 输入数据非法（如 DEX/资源格式不符合规范）时抛出的运行时异常。
 *
 * **Kotlin 转换说明**：只保留原来的单个 `String` 构造器，继承 [JadxRuntimeException]。
 */
class InvalidDataException(message: String) : JadxRuntimeException(message)
