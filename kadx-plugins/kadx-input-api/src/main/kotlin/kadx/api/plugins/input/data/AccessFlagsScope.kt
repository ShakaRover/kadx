package kadx.api.plugins.input.data

/**
 * 访问标志的作用域类型。
 *
 * 在 Dex/Class 文件中，访问标志（如 public、private、static 等）的含义取决于它们应用的位置：
 * - CLASS：类级别的访问标志（如 public class、abstract class）
 * - FIELD：字段级别的访问标志（如 private field、volatile field）
 * - METHOD：方法级别的访问标志（如 protected method、native method）
 *
 * 这个枚举用于区分同一数值在不同上下文中可能代表不同的语义。
 */
public enum class AccessFlagsScope(
	/** 人类可读的描述信息，用于日志和调试输出 */
	public val description: String,
) {
	/** 类级别的访问标志作用域 */
	CLASS("Class-level access flags"),

	/** 字段级别的访问标志作用域 */
	FIELD("Field-level access flags"),

	/** 方法级别的访问标志作用域 */
	METHOD("Method-level access flags"),
}
