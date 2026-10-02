package jadx.tests.api.compiler

import javax.tools.JavaCompiler

/**
 * ECJ（Eclipse Compiler for Java）实例工厂。
 *
 * ECJ 以 `org.eclipse.jdt.internal.compiler.tool.EclipseCompiler` 提供 `JavaCompiler` 实现，
 * 通过反射加载可避免对编译期依赖的硬引用。`newInstance` 保留 `@JvmStatic` 以便 Java 调用。
 */
object EclipseCompilerUtils {

	@JvmStatic
	fun newInstance(): JavaCompiler {
		if (!JavaUtils.checkJavaVersion(11)) {
			throw IllegalArgumentException("Eclipse compiler build with Java 11")
		}
		try {
			val ecjCls = Class.forName("org.eclipse.jdt.internal.compiler.tool.EclipseCompiler")
			return ecjCls.getConstructor().newInstance() as JavaCompiler
		} catch (e: Exception) {
			throw RuntimeException("Failed to init Eclipse compiler", e)
		}
	}
}
