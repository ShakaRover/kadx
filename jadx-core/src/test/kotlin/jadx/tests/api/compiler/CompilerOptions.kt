package jadx.tests.api.compiler

import java.util.Collections

/**
 * 测试用 Java 编译器配置。
 *
 * **为什么保留 Java 风格的 getter/setter**：该类被仍然使用 Java 的 `IntegrationTest.java` 直接调用
 * （`setUseEclipseCompiler` / `setIncludeDebugInfo` / `setJavaVersion` / `addArgument`），
 * 因此属性命名需保证生成的 JVM 访问器与原 Java 完全一致。
 */
class CompilerOptions {
	/** 是否在编译产物中保留调试信息（对应 `-g` / `-g:none`）。 */
	var isIncludeDebugInfo: Boolean = true

	/** 是否使用 ECJ（Eclipse 编译器）替代 javac。 */
	var isUseEclipseCompiler: Boolean = false

	/** 目标 Java 版本；0 表示不显式指定 `-source` / `-target`。 */
	var javaVersion: Int = 8

	/** 附加的编译参数（键值成对追加）。 */
	private var arguments: MutableList<String> = mutableListOf()

	/** 返回只读的编译参数视图（保持与原 Java 的 `unmodifiableList` 语义一致）。 */
	fun getArguments(): List<String> = Collections.unmodifiableList(arguments)

	/** 追加一个无值编译参数。 */
	fun addArgument(argName: String) {
		arguments.add(argName)
	}

	/** 追加一个带值编译参数（如 `-parameters value`）。 */
	fun addArgument(argName: String, argValue: String) {
		arguments.add(argName)
		arguments.add(argValue)
	}
}
