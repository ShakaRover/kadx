@file:Suppress("ktlint:standard:property-naming")

package kadx.core

/**
 * 全局常量集合：调试开关、常用类名、默认包名等。
 *
 * **做什么**：集中存放 kadx 各模块共用的编译期常量与少量运行期常量，
 * 避免魔法字符串散落各处。
 *
 * **为什么用 `object` + `const val`**：原 Java 类只有
 * `public static final` 字段和私有构造器，没有任何实例语义；转成 Kotlin
 * 单例 `object` 后，`const val` 常量在 Java 侧仍是 static final 字段，
 * Kotlin 调用方写 `Consts.XXX`。
 *
 * **注意**：[DEBUG_EVENTS] 的值依赖运行时版本判断（[Kadx.isDevVersion]），
 * 因此不能声明为 `const val`（`const` 要求编译期常量），只能用 `val`。
 */
object Consts {

	// ===== 调试开关（编译期常量，Java 侧同样按 static final 内联） =====

	/** 全局调试开关：打印大量处理细节，仅开发时打开。 */
	const val DEBUG = false

	/** 打印内部错误详情（原注释 TODO: fix errors，表示相关错误尚未完全修复）。 */
	const val DEBUG_WITH_ERRORS = false

	/** 打印用法/引用信息相关调试日志。 */
	const val DEBUG_USAGE = false

	/** 打印类型推断过程的调试日志（热点，开启后输出极多）。 */
	const val DEBUG_TYPE_INFERENCE = false

	/** 打印重载强制转换相关调试日志。 */
	const val DEBUG_OVERLOADED_CASTS = false

	/** 打印异常处理（try/catch/finally）分析日志。 */
	const val DEBUG_EXC_HANDLERS = false

	/** 打印 finally 块恢复相关调试日志。 */
	const val DEBUG_FINALLY = false

	/** 打印属性（attribute）读写日志，用于排查属性何时被添加。 */
	const val DEBUG_ATTRIBUTES = false

	/** 打印控制流区域恢复（restructure）调试日志。 */
	const val DEBUG_RESTRUCTURE = false

	/** 是否打印事件总线调试日志：仅开发版（版本号为 `dev`）打开。 */
	val DEBUG_EVENTS: Boolean = Kadx.isDevVersion()

	// ===== 常用 Java 类型全名 =====

	const val CLASS_OBJECT = "java.lang.Object"
	const val CLASS_STRING = "java.lang.String"
	const val CLASS_CLASS = "java.lang.Class"
	const val CLASS_THROWABLE = "java.lang.Throwable"
	const val CLASS_ERROR = "java.lang.Error"
	const val CLASS_EXCEPTION = "java.lang.Exception"
	const val CLASS_RUNTIME_EXCEPTION = "java.lang.RuntimeException"
	const val CLASS_ENUM = "java.lang.Enum"

	const val CLASS_STRING_BUILDER = "java.lang.StringBuilder"

	/** `@Override` 注解的 DEX 类型描述符。 */
	const val OVERRIDE_ANNOTATION = "Ljava/lang/Override;"

	/** 无包名类使用的默认包名。 */
	const val DEFAULT_PACKAGE_NAME = "defpackage"

	/** 匿名内部类生成名称的前缀。 */
	const val ANONYMOUS_CLASS_PREFIX = "AnonymousClass"

	/** `toString()` 方法的 JVM 签名，用于识别字符串转换调用。 */
	const val MTH_TOSTRING_SIGNATURE = "toString()Ljava/lang/String;"
}
