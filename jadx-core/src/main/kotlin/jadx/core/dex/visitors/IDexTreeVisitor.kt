package jadx.core.dex.visitors

import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.exceptions.JadxException

/**
 * 遍历 DEX 语法树的访问者接口（jadx 各处理 Pass 的统一抽象）。
 *
 * **做什么**：一个 Pass 实现该接口后，就可以对整棵类/方法树按深度优先方式执行。
 * 每个访问者先 `init(root)` 做一次性初始化，然后对每个类调用 [visit]，
 * 再对类中的每个方法调用 [visit]。
 *
 * **为什么保留 Java 风格的 `getName()` / `@Throws`**：本接口被仓库中大量 Java 类实现，
 * 且 `Jadx.java` 的 Pass 列表依赖其精确签名。`@Throws(JadxException::class)` 会把受检异常
 * 写入字节码的 `throws` 子句，这样 Java 实现类仍可声明 `throws JadxException`。
 */
interface IDexTreeVisitor {

	/**
	 * 访问者的短标识（用于日志/调试）。
	 */
	fun getName(): String

	/**
	 * 在加载完 DEX 树、开始遍历之前调用一次。
	 */
	@Throws(JadxException::class)
	fun init(root: RootNode)

	/**
	 * 访问一个类。
	 *
	 * @return 返回 false 表示不再遍历该类的子方法与内部类
	 */
	@Throws(JadxException::class)
	fun visit(cls: ClassNode): Boolean

	/**
	 * 访问一个方法。
	 */
	@Throws(JadxException::class)
	fun visit(mth: MethodNode)
}
