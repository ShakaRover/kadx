package kadx.core.dex.visitors

import kotlin.reflect.KClass

/**
 * 描述 kadx visitor（处理 Pass）之间依赖关系的注解。
 *
 * **做什么**：挂在每个 [IDexTreeVisitor] 实现类上，声明它的短名、描述，以及必须排在
 * 哪些 visitor 之后 / 之前运行。`Kadx` 的 Pass 列表与 `KadxVisitorsOrderTest` 会读取
 * 这些信息来校验/排序执行顺序。
 *
 * **为什么保留运行期可见**：测试通过反射读取注解，因此必须 `RUNTIME` 保留；
 * `runAfter` / `runBefore` 用 `KClass` 数组声明，在 JVM 字节码层面就是
 * `Class<? extends IDexTreeVisitor>[]`，Java 调用方仍可写 `{ Foo.class }`。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class KadxVisitor(
	/** Visitor 的短标识（日志/调试用）。 */
	val name: String,
	/** 详细描述。 */
	val desc: String = "",
	/** 本 visitor 必须运行在列出的 visitor **之后**。 */
	val runAfter: Array<KClass<out IDexTreeVisitor>> = [],
	/** 本 visitor 必须运行在列出的 visitor **之前**。 */
	val runBefore: Array<KClass<out IDexTreeVisitor>> = [],
)
